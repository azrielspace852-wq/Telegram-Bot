import { unzipSync, zipSync } from "fflate";
import { isTextLike, sanitizeFilename, textFromBytes } from "../utils";

export interface ZipEntry { name: string; bytes: Uint8Array }

export function makeZip(entries: ZipEntry[]): Uint8Array {
  const files: Record<string, Uint8Array> = {};
  for (const entry of entries) {
    const safe = entry.name.split("/").map(sanitizeFilename).filter(Boolean).join("/");
    if (!safe) continue;
    files[safe] = entry.bytes;
  }
  return zipSync(files, { level: 6 });
}

export function extractZip(bytes: Uint8Array, maxTotalBytes = 2 * 1024 * 1024): ZipEntry[] {
  const files = unzipSync(bytes);
  const entries: ZipEntry[] = [];
  let total = 0;
  for (const [rawName, data] of Object.entries(files)) {
    const name = rawName.split("/").map(sanitizeFilename).filter(Boolean).join("/");
    if (!name || rawName.endsWith("/")) continue;
    total += data.length;
    if (total > maxTotalBytes) throw new Error("ZIP extracted size exceeds the safety limit");
    entries.push({ name, bytes: data });
  }
  return entries;
}

export function extractZipTextContext(bytes: Uint8Array, maxTotalBytes = 1024 * 1024): string {
  const entries = extractZip(bytes, Math.max(maxTotalBytes, 2 * 1024 * 1024));
  let used = 0;
  const chunks: string[] = [];
  for (const entry of entries) {
    const ext = entry.name.includes(".") ? entry.name.split(".").pop()!.toLowerCase() : "";
    if (!isTextLike(ext)) {
      chunks.push(`FILE: ${entry.name} [binary/unexpanded]`);
      continue;
    }
    const remaining = Math.max(0, maxTotalBytes - used);
    if (!remaining) break;
    const text = textFromBytes(entry.bytes).slice(0, remaining);
    used += text.length;
    chunks.push(`FILE: ${entry.name}\n${text}`);
  }
  return chunks.join("\n\n");
}
