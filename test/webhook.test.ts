import { describe, expect, it } from "vitest";
import worker from "../src/index";
import type { Env, TelegramQueueEnvelope } from "../src/types";

function makeEnv(queue: { send(message: TelegramQueueEnvelope): Promise<void> }): Env {
  return {
    TELEGRAM_BOT_TOKEN: "test-token",
    TELEGRAM_WEBHOOK_SECRET: "test-secret",
    GITHUB_TOKEN: "test-github",
    GEMINI_API_KEY: "test-gemini",
    GITHUB_OWNER: "owner",
    GITHUB_REPO: "repo",
    GITHUB_BRANCH: "main",
    GITHUB_COMMITTER_NAME: "test",
    GITHUB_COMMITTER_EMAIL: "test@example.com",
    GEMINI_MODEL: "gemini-2.5-flash",
    GEMINI_IMAGE_MODEL: "gemini-2.5-flash-image",
    MAX_FILE_BYTES: "8388608",
    RATE_LIMIT_REQUESTS: "10",
    RATE_LIMIT_WINDOW_SECONDS: "60",
    RATE_LIMITER: {
      idFromName: () => ({}),
      get: () => ({ fetch: async () => Response.json({ allowed: true }) })
    } as Env["RATE_LIMITER"],
    INCOMING_QUEUE: queue as Env["INCOMING_QUEUE"]
  };
}

function update() {
  return {
    update_id: 12345,
    message: {
      message_id: 10,
      date: 1,
      chat: { id: 900, type: "private" },
      from: { id: 901, is_bot: false },
      text: "hello"
    }
  };
}

describe("webhook queue handoff", () => {
  it("returns 2xx and queues a valid Telegram message", async () => {
    const received: TelegramQueueEnvelope[] = [];
    const env = makeEnv({ send: async (message) => { received.push(message); } });

    const response = await worker.fetch(new Request("https://example.com/webhook", {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "X-Telegram-Bot-Api-Secret-Token": "test-secret"
      },
      body: JSON.stringify(update())
    }), env);

    expect(response.status).toBe(200);
    expect(received).toHaveLength(1);
    expect(received[0].update_id).toBe(12345);
    expect(received[0].update.update_id).toBe(12345);
  });

  it("rejects a request with an invalid webhook secret", async () => {
    const env = makeEnv({ send: async () => {} });
    const response = await worker.fetch(new Request("https://example.com/webhook", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(update())
    }), env);

    expect(response.status).toBe(403);
  });

  it("returns 503 when queue insertion fails so Telegram can redeliver", async () => {
    const env = makeEnv({ send: async () => { throw new Error("queue unavailable"); } });
    const response = await worker.fetch(new Request("https://example.com/webhook", {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "X-Telegram-Bot-Api-Secret-Token": "test-secret"
      },
      body: JSON.stringify(update())
    }), env);

    expect(response.status).toBe(503);
  });
});
