export interface Env {
  TELEGRAM_BOT_TOKEN: string;
  TELEGRAM_WEBHOOK_SECRET?: string;
  GITHUB_TOKEN: string;
  GEMINI_API_KEY: string;
  GITHUB_OWNER: string;
  GITHUB_REPO: string;
  GITHUB_BRANCH: string;
  GITHUB_COMMITTER_NAME: string;
  GITHUB_COMMITTER_EMAIL: string;
  GEMINI_MODEL: string;
  GEMINI_IMAGE_MODEL: string;
  MAX_FILE_BYTES: string;
  RATE_LIMIT_REQUESTS: string;
  RATE_LIMIT_WINDOW_SECONDS: string;
  RATE_LIMITER: DurableObjectNamespace;
  INCOMING_QUEUE: Queue<TelegramQueueEnvelope>;
}

export interface TelegramQueueEnvelope {
  update_id: number;
  update: TelegramUpdate;
}

export interface TelegramUpdate {
  update_id: number;
  message?: TelegramMessage;
}

export interface TelegramMessage {
  message_id: number;
  date: number;
  chat: { id: number; type: string; title?: string; username?: string; first_name?: string; last_name?: string };
  from?: { id: number; is_bot: boolean; username?: string; first_name?: string; last_name?: string };
  text?: string;
  caption?: string;
  document?: { file_id: string; file_name?: string; mime_type?: string; file_size?: number };
  photo?: Array<{ file_id: string; width: number; height: number; file_size?: number }>;
  audio?: { file_id: string; file_name?: string; mime_type?: string; file_size?: number };
  video?: { file_id: string; file_name?: string; mime_type?: string; file_size?: number };
  voice?: { file_id: string; mime_type?: string; file_size?: number };
  animation?: { file_id: string; file_name?: string; mime_type?: string; file_size?: number };
}

export interface ConversationMessage {
  id: string;
  role: "user" | "model";
  text: string;
  createdAt: string;
  file?: { path: string; name: string; mimeType: string };
}

export interface ConversationRecord {
  version: 1;
  id: string;
  userId: string;
  chatId: number;
  title: string;
  createdAt: string;
  updatedAt: string;
  memoryCursor: number;
  discussedFilePath?: string;
  messages: ConversationMessage[];
}

export interface UserMemoryRecord {
  version: 1;
  userId: string;
  activeConversationId: string | null;
  conversationIds: string[];
  preferredLanguage: "id" | "en";
  updatedAt: string;
  lastProcessedUpdateId?: number;
  processedUpdateIds?: number[];
}

export interface SummaryRecord {
  version: 1;
  userId: string;
  summary: string;
  coveredMessageIds: string[];
  updatedAt: string;
}

export interface StoredFile {
  path: string;
  name: string;
  size?: number;
  sha?: string;
  downloadUrl?: string;
  kind: "sent" | "made";
}

export interface GenerateResult {
  fileName: string;
  mimeType: string;
  bytes: Uint8Array;
}
