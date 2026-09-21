import type { Env, ConversationMessage, ConversationRecord, TelegramQueueEnvelope, TelegramUpdate, UserMemoryRecord } from "./types";
import { GitHubRepo, GitHubError } from "./github";
import { TelegramClient, TelegramError, parseUpdate } from "./telegram";
import { GeminiClient, GeminiError } from "./gemini";
import {
  addMessage,
  ensureActiveConversation,
  getConversation,
  getSummary,
  getUserMemory,
  setUserMemory,
  summarizeIfNeeded
} from "./memory";
import { commandFromText, handleCommand } from "./commands";
import { extensionOf, mimeTypeForExtension, nowIso } from "./utils";
import { storeConversationFile } from "./files";
import { RateLimiter } from "./rateLimit";
import { extractZipTextContext } from "./generators/zip";

export { RateLimiter };

let bootstrapPromise: Promise<void> | null = null;
const MAX_PROCESSED_UPDATE_IDS = 200;

function getMaxFileBytes(env: Env): number {
  const configured = Number(env.MAX_FILE_BYTES);
  return Number.isFinite(configured) && configured > 0
    ? Math.min(configured, 20 * 1024 * 1024)
    : 8 * 1024 * 1024;
}

async function bootstrap(repo: GitHubRepo): Promise<void> {
  if (!bootstrapPromise) {
    bootstrapPromise = repo.bootstrap().catch((error) => {
      bootstrapPromise = null;
      throw error;
    });
  }
  await bootstrapPromise;
}

async function rateLimit(env: Env, userId: string): Promise<boolean> {
  const id = env.RATE_LIMITER.idFromName(userId);
  const stub = env.RATE_LIMITER.get(id);
  const response = await stub.fetch("https://rate.local/check", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      limit: Number(env.RATE_LIMIT_REQUESTS) || 10,
      windowSeconds: Number(env.RATE_LIMIT_WINDOW_SECONDS) || 60
    })
  });
  if (!response.ok) {
    console.error("rate_limiter_unavailable", response.status);
    return true;
  }
  const result = await response.json() as { allowed: boolean };
  return result.allowed;
}

function userIdFromUpdate(update: TelegramUpdate): string | null {
  const id = update.message?.from?.id ?? update.message?.chat.id;
  return typeof id === "number" ? String(id) : null;
}

function hasProcessedUpdate(user: UserMemoryRecord, updateId: number): boolean {
  return user.lastProcessedUpdateId === updateId || Boolean(user.processedUpdateIds?.includes(updateId));
}

async function markProcessed(repo: GitHubRepo, userId: string, updateId: number): Promise<void> {
  const user = await getUserMemory(repo, userId);
  const ids = Array.isArray(user.processedUpdateIds) ? user.processedUpdateIds.filter((id) => id !== updateId) : [];
  ids.push(updateId);
  user.processedUpdateIds = ids.slice(-MAX_PROCESSED_UPDATE_IDS);
  user.lastProcessedUpdateId = updateId;
  user.updatedAt = nowIso();
  await setUserMemory(repo, user);
}

async function processIncomingFile(
  update: TelegramUpdate,
  repo: GitHubRepo,
  telegram: TelegramClient,
  userId: string,
  chatId: number,
  env: Env,
  conversation?: ConversationRecord
): Promise<ConversationMessage | null> {
  const message = update.message;
  if (!message) return null;

  const activeConversation = conversation ?? await ensureActiveConversation(repo, userId, chatId);
  const existing = activeConversation.messages.find((item) => item.id === `file_${update.update_id}`);
  if (existing) return existing;

  const incoming = TelegramClient.getIncomingFile(message);
  if (!incoming) return null;

  const { bytes } = await telegram.getFileBytes(incoming.fileId, getMaxFileBytes(env));
  const stored = await storeConversationFile(
    repo,
    activeConversation.id,
    "sent",
    incoming.filename,
    incoming.mimeType,
    bytes,
    getMaxFileBytes(env),
    `update_${update.update_id}`
  );

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

async function processUpdate(update: TelegramUpdate, env: Env, ctx?: ExecutionContext): Promise<void> {
  const userId = userIdFromUpdate(update);
  const chatId = update.message?.chat.id;
  if (!userId || chatId == null) return;
  if (update.message?.from?.is_bot) return;

  const repo = new GitHubRepo(env);
  const telegram = new TelegramClient(env);
  const gemini = new GeminiClient(env);
  const message = update.message;

  try {
    try {
      await telegram.sendChatAction(chatId, "typing");
    } catch (error) {
      console.warn("typing_indicator_failed", error instanceof Error ? error.message : String(error));
    }

    const preUser = await getUserMemory(repo, userId);
    if (hasProcessedUpdate(preUser, update.update_id)) return;

    await bootstrap(repo);

    const text = message?.text?.trim();
    if (text?.startsWith("/")) {
      const command = commandFromText(text);
      if (command && await handleCommand(command, {
        repo,
        telegram,
        gemini,
        env,
        chatId,
        userId,
        updateId: update.update_id
      })) {
        await markProcessed(repo, userId, update.update_id);
        return;
      }
      if (command) {
        await telegram.sendMessage(chatId, "Perintah tidak dikenal. Gunakan /help.");
        await markProcessed(repo, userId, update.update_id);
        return;
      }
    }

    let conversation = await ensureActiveConversation(repo, userId, chatId);
    const existingModel = conversation.messages.find((item) => item.id === `model_${update.update_id}`);
    if (existingModel) {
      await telegram.sendMessage(chatId, existingModel.text);
      await markProcessed(repo, userId, update.update_id);
      return;
    }

    let userMessage: ConversationMessage | null = null;
    const incomingFile = TelegramClient.getIncomingFile(message ?? ({ } as any));

    if (message?.document && !incomingFile) {
      await telegram.sendMessage(
        chatId,
        "Jenis file tersebut belum diizinkan. Gunakan salah satu ekstensi yang didukung: PDF, MD, TXT, PPT/PPTX, JSON, JSONL, CSV, SQL, DB, DATA, TS, TSX, JS, JAVA, KT, GRADLE, HTML, CSS, CPP, C, JPG, JPEG, PNG, SVG, WEBP, MP4, MP3, WEBM, ZIP, APK."
      );
      await markProcessed(repo, userId, update.update_id);
      return;
    }

    if (incomingFile) {
      userMessage = await processIncomingFile(update, repo, telegram, userId, chatId, env, conversation);
      if (!userMessage) return;

      if (!conversation.messages.some((item) => item.id === userMessage!.id)) {
        conversation = await addMessage(repo, conversation, userMessage);
      }

      if (message?.caption?.trim()) {
        const summary = await getSummary(repo, userId);
        const bytes = userMessage.file ? await repo.read(userMessage.file.path) : null;
        const priorMessages = conversation.messages.filter((item) => item.id !== userMessage!.id);
        const reply = await gemini.chat(
          priorMessages,
          summary,
          message.caption.trim(),
          bytes && userMessage.file
            ? { name: userMessage.file.name, mimeType: userMessage.file.mimeType, bytes }
            : undefined
        );
        const responseMessage: ConversationMessage = {
          id: `model_${update.update_id}`,
          role: "model",
          text: reply,
          createdAt: nowIso()
        };
        await addMessage(repo, conversation, responseMessage);
        await telegram.sendMessage(chatId, reply);
        if (ctx) ctx.waitUntil(summarizeIfNeeded(repo, gemini, conversation.id).catch((error) => console.error("memory_summarization_failed", error instanceof Error ? error.message : String(error))));
      } else {
        await telegram.sendMessage(chatId, `File tersimpan: ${userMessage.file?.name ?? "file"}. Gunakan /discuss <nama_file> untuk membahasnya dengan AI.`);
      }

      await markProcessed(repo, userId, update.update_id);
      return;
    }

    if (!text) return;

    const existingUserMessage = conversation.messages.find((item) => item.id === `user_${update.update_id}`);
    userMessage = existingUserMessage ?? {
      id: `user_${update.update_id}`,
      role: "user",
      text,
      createdAt: nowIso()
    };

    if (!existingUserMessage) conversation = await addMessage(repo, conversation, userMessage);

    const summary = await getSummary(repo, userId);
    let selectedFile: { name: string; mimeType: string; bytes: Uint8Array } | undefined;
    if (conversation.discussedFilePath) {
      const bytes = await repo.read(conversation.discussedFilePath);
      if (bytes) {
        const name = conversation.discussedFilePath.split("/").pop() || conversation.discussedFilePath;
        const ext = extensionOf(name);
        if (ext === "zip") {
          const context = extractZipTextContext(bytes);
          selectedFile = {
            name: `${name} (extracted)`,
            mimeType: "text/plain",
            bytes: new TextEncoder().encode(context)
          };
        } else {
          selectedFile = { name, mimeType: mimeTypeForExtension(ext), bytes };
        }
      }
    }

    const priorMessages = conversation.messages.filter((item) => item.id !== userMessage!.id);
    const reply = await gemini.chat(priorMessages, summary, text, selectedFile);
    const responseMessage: ConversationMessage = {
      id: `model_${update.update_id}`,
      role: "model",
      text: reply,
      createdAt: nowIso()
    };
    await addMessage(repo, conversation, responseMessage);
    await telegram.sendMessage(chatId, reply);
    await markProcessed(repo, userId, update.update_id);

    if (ctx) ctx.waitUntil(summarizeIfNeeded(repo, gemini, conversation.id).catch((error) => console.error("memory_summarization_failed", error instanceof Error ? error.message : String(error))));
  } catch (error) {
    console.error("update_processing_error", {
      updateId: update.update_id,
      chatId,
      error: error instanceof Error ? { name: error.name, message: error.message } : String(error)
    });
    try {
      await telegram.sendMessage(chatId, friendlyError(error));
    } catch (sendError) {
      console.error("error_reply_failed", sendError instanceof Error ? sendError.message : String(sendError));
      throw sendError;
    }
    await markProcessed(repo, userId, update.update_id);
  }
}

function readQueueEnvelope(value: unknown): TelegramUpdate | null {
  if (!value || typeof value !== "object") return null;
  const body = value as Partial<TelegramQueueEnvelope>;
  if (typeof body.update_id !== "number" || !body.update || typeof body.update !== "object") return null;
  if (body.update.update_id !== body.update_id) return null;
  return body.update;
}

async function handleWebhook(request: Request, env: Env): Promise<Response> {
  if (env.TELEGRAM_WEBHOOK_SECRET) {
    const provided = request.headers.get("X-Telegram-Bot-Api-Secret-Token");
    if (provided !== env.TELEGRAM_WEBHOOK_SECRET) return new Response("Forbidden", { status: 403 });
  }

  let update: TelegramUpdate;
  try {
    update = parseUpdate(await request.json());
  } catch (error) {
    return new Response(friendlyError(error), { status: 400 });
  }

  const userId = userIdFromUpdate(update);
  const chatId = update.message?.chat.id;
  if (!userId || chatId == null || update.message?.from?.is_bot) return new Response("OK");

  try {
    if (!(await rateLimit(env, userId))) {
      const telegram = new TelegramClient(env);
      try {
        await telegram.sendMessage(chatId, "Terlalu banyak request. Silakan coba lagi setelah rate limit reset.");
      } catch (error) {
        console.error("rate_limit_reply_failed", error instanceof Error ? error.message : String(error));
      }
      return new Response("OK");
    }

    const envelope: TelegramQueueEnvelope = { update_id: update.update_id, update };
    await env.INCOMING_QUEUE.send(envelope);
    return Response.json({ ok: true, queued: true });
  } catch (error) {
    console.error("webhook_enqueue_error", error instanceof Error ? { name: error.name, message: error.message } : String(error));
    return new Response("Queue unavailable", { status: 503 });
  }
}

const worker = {
  async fetch(request: Request, env: Env): Promise<Response> {
    try {
      const url = new URL(request.url);
      if (url.pathname === "/health" && request.method === "GET") {
        return Response.json({ ok: true, service: "neuralis-telegram-bot", time: nowIso() });
      }
      if (url.pathname === "/webhook" && request.method === "POST") {
        return handleWebhook(request, env);
      }
      return new Response("Not Found", { status: 404 });
    } catch (error) {
      console.error("worker_error", error instanceof Error ? error.message : String(error));
      return new Response("Internal Server Error", { status: 500 });
    }
  },

  async queue(batch: MessageBatch<TelegramQueueEnvelope>, env: Env, ctx: ExecutionContext): Promise<void> {
    for (const message of batch.messages) {
      const update = readQueueEnvelope(message.body);
      if (!update) {
        console.error("invalid_queue_message", { messageId: message.id });
        message.ack();
        continue;
      }

      try {
        await processUpdate(update, env, ctx);
        message.ack();
      } catch (error) {
        const delaySeconds = Math.min(300, Math.max(5, message.attempts * 15));
        console.error("queue_update_failed", {
          queueMessageId: message.id,
          updateId: update.update_id,
          attempts: message.attempts,
          delaySeconds,
          error: error instanceof Error ? { name: error.name, message: error.message } : String(error)
        });
        message.retry({ delaySeconds });
      }
    }
  }
};

export default worker;
