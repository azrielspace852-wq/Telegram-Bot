import type { ConversationMessage, ConversationRecord, Env, SummaryRecord, UserMemoryRecord } from "./types";
import { GitHubRepo } from "./github";
import { nowIso, safeId, randomConversationId } from "./utils";
import { GeminiClient } from "./gemini";

export const userMemoryPath = (userId: string) => `memories/user_${safeId(userId)}.json`;
export const summaryPath = (userId: string) => `memories/summary_${safeId(userId)}.json`;
export const conversationPath = (conversationId: string) => `Conversation/${safeId(conversationId)}.json`;

export function emptyUserMemory(userId: string): UserMemoryRecord {
  return {
    version: 1,
    userId,
    activeConversationId: null,
    conversationIds: [],
    preferredLanguage: "id",
    updatedAt: nowIso(),
    processedUpdateIds: []
  };
}

export async function getUserMemory(repo: GitHubRepo, userId: string): Promise<UserMemoryRecord> {
  return repo.readJson<UserMemoryRecord>(userMemoryPath(userId), emptyUserMemory(userId));
}

export async function setUserMemory(repo: GitHubRepo, user: UserMemoryRecord): Promise<void> {
  await repo.upsertJson(userMemoryPath(user.userId), user, `Update user memory ${user.userId}`);
}

export async function getSummary(repo: GitHubRepo, userId: string): Promise<SummaryRecord | null> {
  return repo.readJson<SummaryRecord | null>(summaryPath(userId), null);
}

export async function getConversation(repo: GitHubRepo, conversationId: string): Promise<ConversationRecord | null> {
  return repo.readJson<ConversationRecord | null>(conversationPath(conversationId), null);
}

export async function createConversation(repo: GitHubRepo, userId: string, chatId: number, title = "New conversation"): Promise<ConversationRecord> {
  const id = randomConversationId(userId);
  const timestamp = nowIso();
  const record: ConversationRecord = {
    version: 1,
    id,
    userId,
    chatId,
    title: title.slice(0, 120),
    createdAt: timestamp,
    updatedAt: timestamp,
    memoryCursor: 0,
    messages: []
  };
  await repo.upsertJson(conversationPath(id), record, `Create conversation ${id}`);
  const user = await getUserMemory(repo, userId);
  user.activeConversationId = id;
  if (!user.conversationIds.includes(id)) user.conversationIds.push(id);
  user.updatedAt = timestamp;
  await setUserMemory(repo, user);
  return record;
}

export async function ensureActiveConversation(repo: GitHubRepo, userId: string, chatId: number): Promise<ConversationRecord> {
  const user = await getUserMemory(repo, userId);
  if (user.activeConversationId) {
    const current = await getConversation(repo, user.activeConversationId);
    if (current) return current;
  }
  return createConversation(repo, userId, chatId, "Conversation 1");
}

export async function addMessage(repo: GitHubRepo, conversation: ConversationRecord, message: ConversationMessage): Promise<ConversationRecord> {
  if (conversation.messages.some((existing) => existing.id === message.id)) return conversation;
  conversation.messages.push(message);
  conversation.updatedAt = nowIso();
  await repo.upsertJson(conversationPath(conversation.id), conversation, `Append message ${message.id}`);
  return conversation;
}

export async function summarizeIfNeeded(repo: GitHubRepo, gemini: GeminiClient, conversationId: string): Promise<void> {
  const conversation = await getConversation(repo, conversationId);
  if (!conversation) return;
  if (conversation.messages.length - conversation.memoryCursor < 20) return;

  const batch = conversation.messages.slice(conversation.memoryCursor, conversation.memoryCursor + 20);
  if (batch.length < 20) return;
  const existing = await getSummary(repo, conversation.userId);
  const summaryText = await gemini.summarize(existing, batch);
  const summary: SummaryRecord = {
    version: 1,
    userId: conversation.userId,
    summary: summaryText,
    coveredMessageIds: [...(existing?.coveredMessageIds ?? []), ...batch.map((m) => m.id)].slice(-500),
    updatedAt: nowIso()
  };
  await repo.upsertJson(summaryPath(conversation.userId), summary, `Summarize long-term memory ${conversation.userId}`);
  const latest = await getConversation(repo, conversation.id);
  if (!latest) return;
  latest.memoryCursor = Math.max(latest.memoryCursor, conversation.memoryCursor + batch.length);
  latest.updatedAt = nowIso();
  await repo.upsertJson(conversationPath(latest.id), latest, `Advance memory cursor ${latest.id}`);
}
