export class RateLimiter {
  private initialized = false;

  constructor(private readonly state: DurableObjectState) {
    state.blockConcurrencyWhile(async () => {
      if (!this.state.storage.sql) return;
      this.state.storage.sql.exec(`CREATE TABLE IF NOT EXISTS windows (id TEXT PRIMARY KEY, count INTEGER NOT NULL, reset_at INTEGER NOT NULL)`);
      this.initialized = true;
    });
  }

  async fetch(request: Request): Promise<Response> {
    if (request.method !== "POST") return new Response("Method Not Allowed", { status: 405 });
    const data = await request.json() as { limit: number; windowSeconds: number };
    const now = Math.floor(Date.now() / 1000);
    const windowSeconds = Math.max(1, Math.floor(data.windowSeconds || 60));
    const limit = Math.max(1, Math.floor(data.limit || 10));
    const id = "global";

    // Each DO is dedicated to one Telegram user, so the key can remain constant.
    const row = this.state.storage.sql?.exec<{ count: number; reset_at: number }>(
      `SELECT count, reset_at FROM windows WHERE id = ?`, id
    ).toArray()[0];

    let count = row?.count ?? 0;
    let resetAt = row?.reset_at ?? now + windowSeconds;
    if (now >= resetAt) {
      count = 0;
      resetAt = now + windowSeconds;
    }

    count += 1;
    this.state.storage.sql?.exec(
      `INSERT INTO windows (id, count, reset_at) VALUES (?, ?, ?)
       ON CONFLICT(id) DO UPDATE SET count=excluded.count, reset_at=excluded.reset_at`,
      id, count, resetAt
    );

    return Response.json({ allowed: count <= limit, count, remaining: Math.max(0, limit - count), resetAt });
  }
}
