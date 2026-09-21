import type { Env, ConversationMessage, TelegramUpdate } from "./types";
import { GitHubRepo, GitHubError } from "./github";
import { TelegramClient, TelegramError, parseUpdate } from "./telegram";
import { GeminiClient, GeminiError } from "./gemini";
import { addMessage, ensureActiveConversation, getConversation, getSummary, getUserMemory, setUserMemory, summarizeIfNeeded } from "./memory";
import { commandFromText, handleCommand } from "./commands";
import { extensionOf, mimeTypeForExtension, nowIso } from "./utils";
import { storeConversationFile } from "./files";
import { RateLimiter } from "./rateLimit";
import { extractZipTextContext } from "./generators/zip";

export { RateLimiter };

let bootstrapPromise: Promise<void> | null = null;

function getMaxFileBytes(env: Env): number {
  const configured = Number(env.MAX_FILE_BYTES);
  return Number.isFinite(configured) && configured > 0 ? Math.min(configured, 20 * 1024 * 1024) : 8 * 1024 * 1024;
}

async function bootstrap(repo: GitHubRepo): Promise<void> {
  if (!bootstrapPromise) bootstrapPromise = repo.bootstrap();
  await bootstrapPromise;
}

async function rateLimit(env: Env, userId: string): Promise<boolean> {
  const id = env.RATE_LIMITER.idFromName(userId);
  const stub = env.RATE_LIMITER.get(id);
  const response = await stub.fetch("https://rate.local/check", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ limit: Number(env.RATE_LIMIT_REQUESTS) || 10, windowSeconds: Number(env.RATE_LIMIT_WINDOW_SECONDS) || 60 })
  });
  if (!response.ok) return true;
  const result = await response.json() as { allowed: boolean };
  return result.allowed;
}

function userIdFromUpdate(update: TelegramUpdate): string | null {
  const id = update.message?.from?.id ?? update.message?.chat.id;
  return typeof id === "number" ? String(id) : null;
}

async function processIncomingFile(update: TelegramUpdate, repo: GitHubRepo, telegram: TelegramClient, userId: string, chatId: number, env: Env): Promise<ConversationMessage | null> {
  const message = update.message;
  if (!message) return null;
  const incoming = TelegramClient.getIncomingFile(message);
  if (!incoming) return null;
  const conversation = await ensureActiveConversation(repo, userId, chatId);
  const { bytes } = await telegram.getFileBytes(incoming.fileId, getMaxFileBytes(env));
  const stored = await storeConversationFile(repo, conversation.id, "sent", incoming.filename, incoming.mimeType, bytes, getMaxFileBytes(env), `update_${update.update_id}`);
  return {
    id: `file_${update.update_id}`,
    role: "user",
    text: `[User uploaded file: ${stored.name}]${message.caption ? ` Caption: ${message.caption}` : ""}`,
    createdAt: nowIso(),
    file: { path: stored.path, name: stored.name, mimeType: stored.mimeType }
  };
}

function friendlyError(error: unknown): string {
  if (error instanceof GeminiError) return `AI error: ${error.message}`;
  if (error instanceof TelegramError) return `Telegram error: ${error.message}`;
  if (error instanceof GitHubError) {
    if (error.status === 401 || error.status === 403) return "Storage authorization failed. Periksa GitHub Token dan permission repository.";
    if (error.status === 409) return "Storage sedang menerima perubahan bersamaan. Silakan ulangi.";
    if (error.status === 429) return "GitHub sedang membatasi request. Silakan coba lagi beberapa saat lagi.";
    return `Storage error: ${error.message}`;
  }
  if (error instanceof Error) return error.message;
  return "Terjadi kesalahan internal yang tidak terduga.";
}

async function handleWebhook(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
  if (env.TELEGRAM_WEBHOOK_SECRET) {
    const provided = request.headers.get("X-Telegram-Bot-Api-Secret-Token");
    if (provided !== env.TELEGRAM_WEBHOOK_SECRET) return new Response("Forbidden", { status: 403 });
  }

  let update: TelegramUpdate;
  try { update = parseUpdate(await request.json()); }
  catch (error) { return new Response(friendlyError(error), { status: 400 }); }

  const userId = userIdFromUpdate(update);
  const chatId = update.message?.chat.id;
  if (!userId || chatId == null) return new Response("OK");
  if (update.message?.from?.is_bot) return new Response("OK");
  if (!(await rateLimit(env, userId))) {
    const telegram = new TelegramClient(env);
    await telegram.sendMessage(chatId, "Terlalu banyak request. Silakan coba lagi setelah rate limit reset.");
    return new Response("OK");
  }

  const repo = new GitHubRepo(env);
  const telegram = new TelegramClient(env);
  const gemini = new GeminiClient(env);
  const message = update.message;

  try {
    const preUser = await getUserMemory(repo, userId);
    if (preUser.lastProcessedUpdateId != null && update.update_id <= preUser.lastProcessedUpdateId) return new Response("OK");
    await bootstrap(repo);

    const text = message?.text?.trim();
    if (text?.startsWith("/")) {
      const command = commandFromText(text);
      if (command && await handleCommand(command, { repo, telegram, gemini, env, chatId, userId, updateId: update.update_id })) {
        const done = await getUserMemory(repo, userId);
        done.lastProcessedUpdateId = update.update_id;
        done.updatedAt = nowIso();
        await setUserMemory(repo, done);
        return new Response("OK");
      }
      if (command) {
        await telegram.sendMessage(chatId, "Perintah tidak dikenal. Gunakan /help.");
        return new Response("OK");
      }
    }

    let conversation = await ensureActiveConversation(repo, userId, chatId);
    let userMessage: ConversationMessage | null = null;
    const incomingFile = TelegramClient.getIncomingFile(message ?? ({ } as any));

    if (message?.document && !incomingFile) {
      await telegram.sendMessage(chatId, "Jenis file tersebut belum diizinkan. Gunakan salah satu ekstensi yang didukung: PDF, MD, TXT, PPT/PPTX, JSON, JSONL, CSV, SQL, DB, DATA, TS, TSX, JS, JAVA, KT, GRADLE, HTML, CSS, CPP, C, JPG, JPEG, PNG, SVG, WEBP, MP4, MP3, WEBM, ZIP, APK.");
      return new Response("OK");
    }

    if (incomingFile) {
      userMessage = await processIncomingFile(update, repo, telegram, userId, chatId, env);
      if (userMessage) await addMessage(repo, conversation, userMessage);
      if (message?.caption?.trim()) {
        const summary = await getSummary(repo, userId);
        const bytes = userMessage?.file ? await repo.read(userMessage.file.path) : null;
        const priorMessages = conversation.messages.slice(0, -1);
        const reply = await gemini.chat(priorMessages, summary, message.caption.trim(), bytes && userMessage?.file ? { name: userMessage.file.name, mimeType: userMessage.file.mimeType, bytes } : undefined);
        const responseMessage: ConversationMessage = { id: `model_${update.update_id}`, role: "model", text: reply, createdAt: nowIso() };
        await addMessage(repo, conversation, responseMessage);
        await telegram.sendMessage(chatId, reply);
        await summarizeIfNeeded(repo, gemini, conversation.id);
      } else {
        await telegram.sendMessage(chatId, `File tersimpan: ${userMessage?.file?.name ?? "file"}. Gunakan /discuss <nama_file> untuk membahasnya dengan AI.`);
      }
      const done = await getUserMemory(repo, userId);
      done.lastProcessedUpdateId = update.update_id;
      done.updatedAt = nowIso();
      await setUserMemory(repo, done);
      return new Response("OK");
    }

    if (!text) return new Response("OK");

    userMessage = { id: `user_${update.update_id}`, role: "user", text, createdAt: nowIso() };
    await addMessage(repo, conversation, userMessage);
    const summary = await getSummary(repo, userId);
    let selectedFile: { name: string; mimeType: string; bytes: Uint8Array } | undefined;
    if (conversation.discussedFilePath) {
      const bytes = await repo.read(conversation.discussedFilePath);
      if (bytes) {
        const name = conversation.discussedFilePath.split("/").pop() || conversation.discussedFilePath;
        const ext = extensionOf(name);
        if (ext === "zip") {
          const context = extractZipTextContext(bytes);
          selectedFile = { name: `${name} (extracted)`, mimeType: "text/plain", bytes: new TextEncoder().encode(context) };
        } else {
          selectedFile = { name, mimeType: mimeTypeForExtension(ext), bytes };
        }
      }
    }
    const reply = await gemini.chat(conversation.messages.slice(0, -1), summary, text, selectedFile);
    const responseMessage: ConversationMessage = { id: `model_${update.update_id}`, role: "model", text: reply, createdAt: nowIso() };
    await addMessage(repo, conversation, responseMessage);
    await telegram.sendMessage(chatId, reply);
    const done = await getUserMemory(repo, userId);
    done.lastProcessedUpdateId = update.update_id;
    done.updatedAt = nowIso();
    await setUserMemory(repo, done);
    ctx.waitUntil(summarizeIfNeeded(repo, gemini, conversation.id).catch((error) => console.error("memory_summarization_failed", error instanceof Error ? error.message : String(error))));
    return new Response("OK");
  } catch (error) {
    console.error("webhook_error", error instanceof Error ? { name: error.name, message: error.message } : String(error));
    try { await telegram.sendMessage(chatId, friendlyError(error)); } catch (sendError) { console.error("error_reply_failed", sendError instanceof Error ? sendError.message : String(sendError)); }
    return new Response("OK");
  }
}

const worker = {
  async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
    try {
      const url = new URL(request.url);
      if (url.pathname === "/health" && request.method === "GET") return Response.json({ ok: true, service: "neuralis-telegram-bot", time: nowIso() });
      if (url.pathname === "/webhook" && request.method === "POST") return handleWebhook(request, env, ctx);
      return new Response("Not Found", { status: 404 });
    } catch (error) {
      console.error("worker_error", error instanceof Error ? error.message : String(error));
      return new Response("Internal Server Error", { status: 500 });
    }
  }
};

export default worker;
