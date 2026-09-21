import type { Env, StoredFile } from "./types";
import { GitHubRepo } from "./github";
import { extensionOf, isAllowedExtension, mimeTypeForExtension, sanitizeFilename } from "./utils";
import { extractZip } from "./generators/zip";

export class FileProcessorError extends Error {
  constructor(message: string, public status = 400) { super(message); this.name = "FileProcessorError"; }
}

export function assertFileAllowed(filename: string, maxBytes: number, size: number): void {
  const ext = extensionOf(filename);
  if (!isAllowedExtension(ext)) throw new FileProcessorError(`Unsupported file type: .${ext || "unknown"}`);
  if (size > maxBytes) throw new FileProcessorError(`File exceeds the ${Math.round(maxBytes / 1024 / 1024)} MB safety limit`, 413);
}

export async function storeConversationFile(repo: GitHubRepo, conversationId: string, kind: "sent" | "made", filename: string, mimeType: string, bytes: Uint8Array, maxBytes: number, idempotencyKey?: string): Promise<{ path: string; name: string; mimeType: string }> {
  const safe = sanitizeFilename(filename);
  assertFileAllowed(safe, maxBytes, bytes.length);
  const folder = kind === "sent" ? "Sent" : "Made";
  const prefix = idempotencyKey ? sanitizeFilename(idempotencyKey).slice(0, 80) : new Date().toISOString().replace(/[:.]/g, "-");
  const path = `${folder}/${conversationId}/${prefix}_${safe}`;
  await repo.put(path, bytes, `${kind === "sent" ? "Store incoming" : "Store generated"} file ${safe}`);
  return { path, name: safe, mimeType: mimeType || mimeTypeForExtension(extensionOf(safe)) };
}

async function listOrEmpty(repo: GitHubRepo, path: string) {
  try {
    return await repo.list(path);
  } catch (error) {
    if (error instanceof Error && "status" in error && (error as { status?: number }).status === 404) return [];
    throw error;
  }
}

export async function listConversationFiles(repo: GitHubRepo, conversationId: string): Promise<StoredFile[]> {
  const [sent, made] = await Promise.all([
    listOrEmpty(repo, `Sent/${conversationId}`),
    listOrEmpty(repo, `Made/${conversationId}`)
  ]);
  return [
    ...sent.filter((x) => x.type === "file").map((x) => ({ path: x.path, name: x.path.split("/").pop() || x.path, size: x.size, sha: x.sha, kind: "sent" as const })),
    ...made.filter((x) => x.type === "file").map((x) => ({ path: x.path, name: x.path.split("/").pop() || x.path, size: x.size, sha: x.sha, kind: "made" as const }))
  ].sort((a, b) => a.path.localeCompare(b.path));
}

export async function listAllConversations(repo: GitHubRepo, userId: string): Promise<Array<{ id: string; title?: string; updatedAt?: string }>> {
  const files = await repo.list("Conversation");
  const prefix = `${userId}_`;
  const results: Array<{ id: string; title?: string; updatedAt?: string }> = [];
  for (const item of files.filter((x) => x.type === "file" && x.path.endsWith(".json"))) {
    const id = (item.path.split("/").pop() || "").replace(/\.json$/, "");
    if (!id.startsWith(prefix)) continue;
    results.push({ id });
  }
  return results;
}

export async function describeZip(bytes: Uint8Array): Promise<string> {
  try {
    const entries = extractZip(bytes);
    const first = entries.slice(0, 50).map((e) => `${e.name} (${e.bytes.length} bytes)`);
    return `ZIP contains ${entries.length} file(s):\n${first.join("\n")}${entries.length > 50 ? "\n..." : ""}`;
  } catch (error) {
    throw new FileProcessorError(`Could not extract ZIP: ${String(error)}`, 422);
  }
}
