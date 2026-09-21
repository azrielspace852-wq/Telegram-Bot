import type { Env, TelegramMessage, TelegramUpdate } from "./types";
import { chunkText, extensionOf, isAllowedExtension, mimeTypeForExtension, sanitizeFilename } from "./utils";

export class TelegramError extends Error {
  constructor(message: string, public status = 500, public description?: string) {
    super(message);
    this.name = "TelegramError";
  }
}

export class TelegramClient {
  private readonly base: string;

  constructor(private readonly env: Env) {
    this.base = `https://api.telegram.org/bot${env.TELEGRAM_BOT_TOKEN}`;
  }

  private async api<T>(method: string, body?: unknown): Promise<T> {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 30000);
    const init: RequestInit = { method: "POST", signal: controller.signal };
    if (body !== undefined) {
      init.headers = { "Content-Type": "application/json" };
      init.body = JSON.stringify(body);
    }
    try {
      const response = await fetch(`${this.base}/${method}`, init);
      const data = await response.json() as { ok: boolean; result?: T; description?: string; error_code?: number; parameters?: Record<string, unknown> };
      if (!response.ok || !data.ok) {
        throw new TelegramError(data.description || `Telegram API error (${response.status})`, response.status, data.description);
      }
      return data.result as T;
    } catch (error) {
      if (error instanceof TelegramError) throw error;
      if (error instanceof Error && error.name === "AbortError") throw new TelegramError("Telegram request timed out", 504);
      throw new TelegramError("Telegram request failed", 502, String(error));
    } finally {
      clearTimeout(timer);
    }
  }

  async sendChatAction(chatId: number, action: "typing" = "typing"): Promise<void> {
    await this.api("sendChatAction", { chat_id: chatId, action });
  }

  async sendMessage(chatId: number, text: string): Promise<void> {
    for (const part of chunkText(text)) {
      await this.api("sendMessage", { chat_id: chatId, text: part, disable_web_page_preview: true });
    }
  }

  async sendDocument(chatId: number, filename: string, bytes: Uint8Array, mimeType: string, caption?: string): Promise<void> {
    const form = new FormData();
    form.set("chat_id", String(chatId));
    form.set("document", new File([bytes], filename, { type: mimeType }), filename);
    if (caption) form.set("caption", caption.slice(0, 1024));

    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 30000);
    let response: Response;
    try {
      response = await fetch(`${this.base}/sendDocument`, { method: "POST", body: form, signal: controller.signal });
    } catch (error) {
      if (error instanceof Error && error.name === "AbortError") throw new TelegramError("Telegram upload timed out", 504);
      throw new TelegramError("Telegram upload failed", 502, String(error));
    } finally {
      clearTimeout(timer);
    }
    const data = await response.json() as { ok: boolean; description?: string; error_code?: number };
    if (!response.ok || !data.ok) throw new TelegramError(data.description || "Failed to send document", response.status, data.description);
  }

  async getFileBytes(fileId: string, maxBytes: number): Promise<{ bytes: Uint8Array; filePath: string }> {
    const file = await this.api<{ file_id: string; file_unique_id: string; file_size?: number; file_path?: string }>("getFile", { file_id: fileId });
    if (!file.file_path) throw new TelegramError("Telegram did not return a file path", 502);
    if ((file.file_size ?? 0) > maxBytes) throw new TelegramError(`File is larger than the configured ${Math.round(maxBytes / 1024 / 1024)} MB limit`, 413);
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 30000);
    let response: Response;
    try {
      response = await fetch(`https://api.telegram.org/file/bot${this.env.TELEGRAM_BOT_TOKEN}/${file.file_path}`, { signal: controller.signal });
    } catch (error) {
      if (error instanceof Error && error.name === "AbortError") throw new TelegramError("Telegram file download timed out", 504);
      throw new TelegramError("Telegram file download failed", 502, String(error));
    } finally {
      clearTimeout(timer);
    }
    if (!response.ok) throw new TelegramError(`Failed to download file (${response.status})`, response.status);
    const length = Number(response.headers.get("content-length") || 0);
    if (length > maxBytes) throw new TelegramError(`File is larger than the configured ${Math.round(maxBytes / 1024 / 1024)} MB limit`, 413);
    const bytes = new Uint8Array(await response.arrayBuffer());
    if (bytes.length > maxBytes) throw new TelegramError(`File is larger than the configured ${Math.round(maxBytes / 1024 / 1024)} MB limit`, 413);
    return { bytes, filePath: file.file_path };
  }

  static getIncomingFile(message: TelegramMessage): { fileId: string; filename: string; mimeType: string } | null {
    if (message.document) {
      const filename = sanitizeFilename(message.document.file_name || "document.bin");
      const ext = extensionOf(filename);
      if (!isAllowedExtension(ext)) return null;
      return { fileId: message.document.file_id, filename, mimeType: message.document.mime_type || mimeTypeForExtension(ext) };
    }
    if (message.photo?.length) {
      const largest = [...message.photo].sort((a, b) => (b.file_size ?? 0) - (a.file_size ?? 0)).at(0)!;
      return { fileId: largest.file_id, filename: `photo_${message.message_id}.jpg`, mimeType: "image/jpeg" };
    }
    if (message.audio) {
      const filename = sanitizeFilename(message.audio.file_name || `audio_${message.message_id}.mp3`);
      return { fileId: message.audio.file_id, filename, mimeType: message.audio.mime_type || mimeTypeForExtension(extensionOf(filename)) };
    }
    if (message.video) {
      const filename = sanitizeFilename(message.video.file_name || `video_${message.message_id}.mp4`);
      return { fileId: message.video.file_id, filename, mimeType: message.video.mime_type || mimeTypeForExtension(extensionOf(filename)) };
    }
    if (message.voice) return { fileId: message.voice.file_id, filename: `voice_${message.message_id}.ogg`, mimeType: message.voice.mime_type || "audio/ogg" };
    if (message.animation) {
      const filename = sanitizeFilename(message.animation.file_name || `animation_${message.message_id}.mp4`);
      return { fileId: message.animation.file_id, filename, mimeType: message.animation.mime_type || mimeTypeForExtension(extensionOf(filename)) };
    }
    return null;
  }
}

export function parseUpdate(body: unknown): TelegramUpdate {
  if (!body || typeof body !== "object") throw new TelegramError("Invalid Telegram update", 400);
  const value = body as TelegramUpdate;
  if (typeof value.update_id !== "number") throw new TelegramError("Invalid Telegram update_id", 400);
  return value;
}
