export function nowIso(): string {
  return new Date().toISOString();
}

export function safeId(input: string): string {
  return input.replace(/[^a-zA-Z0-9_-]/g, "_").slice(0, 80) || "unknown";
}

export function sanitizeFilename(input: string): string {
  const cleaned = input
    .normalize("NFKC")
    .replace(/[\\/<>:"|?*\u0000-\u001F]/g, "_")
    .replace(/\.\.+/g, ".")
    .trim();
  const basename = cleaned.split(/[\\/]/).pop() || "file";
  return basename.slice(0, 160) || "file";
}

export function extensionOf(name: string): string {
  const match = name.toLowerCase().match(/\.([a-z0-9]+)$/);
  return match?.[1] ?? "";
}

export function mimeTypeForExtension(ext: string): string {
  const map: Record<string, string> = {
    pdf: "application/pdf",
    md: "text/markdown",
    txt: "text/plain",
    pptx: "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    ppt: "application/vnd.ms-powerpoint",
    json: "application/json",
    jsonl: "application/x-ndjson",
    csv: "text/csv",
    sql: "application/sql",
    db: "application/octet-stream",
    data: "application/octet-stream",
    ts: "text/typescript",
    tsx: "text/typescript",
    js: "text/javascript",
    java: "text/x-java-source",
    kt: "text/plain",
    gradle: "text/plain",
    html: "text/html",
    css: "text/css",
    cpp: "text/x-c++src",
    c: "text/x-c",
    jpg: "image/jpeg",
    jpeg: "image/jpeg",
    png: "image/png",
    svg: "image/svg+xml",
    webp: "image/webp",
    mp4: "video/mp4",
    mp3: "audio/mpeg",
    webm: "video/webm",
    zip: "application/zip",
    apk: "application/vnd.android.package-archive"
  };
  return map[ext] ?? "application/octet-stream";
}

export function isTextLike(ext: string): boolean {
  return new Set([
    "md", "txt", "json", "jsonl", "csv", "sql", "ts", "tsx", "js", "java", "kt", "gradle", "html", "css", "cpp", "c", "data", "svg"
  ]).has(ext);
}

export function isAllowedExtension(ext: string): boolean {
  return new Set([
    "pdf", "md", "txt", "ppt", "pptx", "json", "jsonl", "csv", "sql", "db", "data",
    "ts", "tsx", "js", "java", "kt", "gradle", "html", "css", "cpp", "c",
    "jpg", "jpeg", "png", "svg", "webp", "mp4", "mp3", "webm", "zip", "apk"
  ]).has(ext);
}

export function chunkText(text: string, max = 3900): string[] {
  if (text.length <= max) return [text];
  const result: string[] = [];
  for (let i = 0; i < text.length; i += max) result.push(text.slice(i, i + max));
  return result;
}

export function jsonBytes(value: unknown): Uint8Array {
  return new TextEncoder().encode(JSON.stringify(value, null, 2));
}

export function textBytes(value: string): Uint8Array {
  return new TextEncoder().encode(value);
}

export function bytesToBase64(bytes: Uint8Array): string {
  let binary = "";
  const chunk = 0x8000;
  for (let i = 0; i < bytes.length; i += chunk) {
    binary += String.fromCharCode(...bytes.subarray(i, i + chunk));
  }
  return btoa(binary);
}

export function base64ToBytes(input: string): Uint8Array {
  const binary = atob(input);
  const out = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) out[i] = binary.charCodeAt(i);
  return out;
}

export function textFromBytes(bytes: Uint8Array): string {
  return new TextDecoder().decode(bytes);
}

export async function sha256Hex(bytes: Uint8Array): Promise<string> {
  const hash = await crypto.subtle.digest("SHA-256", bytes);
  return Array.from(new Uint8Array(hash)).map((b) => b.toString(16).padStart(2, "0")).join("");
}

export function parseCommand(text: string): { command: string; args: string } | null {
  const match = text.trim().match(/^\/(\w+)(?:@\w+)?(?:\s+([\s\S]*))?$/);
  return match ? { command: match[1].toLowerCase(), args: (match[2] ?? "").trim() } : null;
}

export function randomConversationId(userId: string): string {
  return `${safeId(userId)}_${crypto.randomUUID()}`;
}
