import { base64ToBytes, bytesToBase64 } from "./utils";
import type { Env } from "./types";

interface GithubContentFile {
  type: "file" | "dir";
  path: string;
  sha?: string;
  size?: number;
  content?: string;
  encoding?: string;
}

export class GitHubError extends Error {
  constructor(message: string, public status: number, public detail?: string) {
    super(message);
    this.name = "GitHubError";
  }
}

export class GitHubRepo {
  private readonly base = "https://api.github.com";
  private readonly owner: string;
  private readonly repo: string;
  private readonly branch: string;

  constructor(private readonly env: Env) {
    this.owner = env.GITHUB_OWNER;
    this.repo = env.GITHUB_REPO;
    this.branch = env.GITHUB_BRANCH || "main";
  }

  private headers(extra: Record<string, string> = {}): Headers {
    return new Headers({
      Accept: "application/vnd.github+json",
      Authorization: `Bearer ${this.env.GITHUB_TOKEN}`,
      "X-GitHub-Api-Version": "2026-03-10",
      "User-Agent": "neuralis-telegram-bot",
      ...extra
    });
  }

  private url(path: string, query = ""): string {
    const clean = path.split("/").filter(Boolean).map(encodeURIComponent).join("/");
    return `${this.base}/repos/${encodeURIComponent(this.owner)}/${encodeURIComponent(this.repo)}/contents/${clean}${query}`;
  }

  async request<T>(url: string, init: RequestInit = {}): Promise<T> {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 30000);
    try {
      const response = await fetch(url, { ...init, signal: controller.signal, headers: this.headers(Object.fromEntries(new Headers(init.headers).entries())) });
      const body = await response.text();
      if (!response.ok) {
        const retryAfter = response.headers.get("retry-after");
        const remaining = response.headers.get("x-ratelimit-remaining");
        const detail = `${body.slice(0, 600)}${retryAfter ? `; retry-after=${retryAfter}` : ""}${remaining === "0" ? "; rate-limit-remaining=0" : ""}`;
        throw new GitHubError(`GitHub API error ${response.status}`, response.status, detail);
      }
      return body ? JSON.parse(body) as T : (undefined as T);
    } catch (error) {
      if (error instanceof GitHubError) throw error;
      if (error instanceof Error && error.name === "AbortError") throw new GitHubError("GitHub request timed out", 504);
      throw new GitHubError("GitHub request failed", 502, String(error));
    } finally {
      clearTimeout(timer);
    }
  }

  async list(path: string): Promise<GithubContentFile[]> {
    const result = await this.request<GithubContentFile[]>(this.url(path, `?ref=${encodeURIComponent(this.branch)}`));
    return Array.isArray(result) ? result : [];
  }

  async exists(path: string): Promise<boolean> {
    try {
      await this.request<GithubContentFile>(this.url(path, `?ref=${encodeURIComponent(this.branch)}`));
      return true;
    } catch (error) {
      if (error instanceof GitHubError && error.status === 404) return false;
      throw error;
    }
  }

  async read(path: string): Promise<Uint8Array | null> {
    try {
      const meta = await this.request<GithubContentFile>(this.url(path, `?ref=${encodeURIComponent(this.branch)}`));
      if (meta.type !== "file" || !meta.sha) return null;
      if (meta.content && meta.encoding === "base64") return base64ToBytes(meta.content.replace(/\n/g, ""));

      // Large-content fallback: request the Git blob in raw form.
      const blobUrl = `${this.base}/repos/${encodeURIComponent(this.owner)}/${encodeURIComponent(this.repo)}/git/blobs/${encodeURIComponent(meta.sha)}`;
      const response = await fetch(blobUrl, {
        headers: this.headers({ Accept: "application/vnd.github.raw+json" })
      });
      if (!response.ok) throw new GitHubError(`GitHub blob read failed (${response.status})`, response.status, await response.text());
      return new Uint8Array(await response.arrayBuffer());
    } catch (error) {
      if (error instanceof GitHubError && error.status === 404) return null;
      throw error;
    }
  }

  async readJson<T>(path: string, fallback: T): Promise<T> {
    const bytes = await this.read(path);
    if (!bytes) return fallback;
    try {
      return JSON.parse(new TextDecoder().decode(bytes)) as T;
    } catch (error) {
      throw new GitHubError(`Stored JSON is corrupt: ${path}`, 422, String(error));
    }
  }

  async put(path: string, bytes: Uint8Array, message: string, sha?: string): Promise<void> {
    const body = {
      message,
      content: bytesToBase64(bytes),
      branch: this.branch,
      committer: {
        name: this.env.GITHUB_COMMITTER_NAME,
        email: this.env.GITHUB_COMMITTER_EMAIL
      },
      sha
    };

    for (let attempt = 0; attempt < 2; attempt++) {
      try {
        await this.request<unknown>(this.url(path), {
          method: "PUT",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(body)
        });
        return;
      } catch (error) {
        if (error instanceof GitHubError && error.status === 409 && attempt === 0) {
          const latest = await this.request<GithubContentFile>(this.url(path, `?ref=${encodeURIComponent(this.branch)}`));
          body.sha = latest.sha;
          continue;
        }
        throw error;
      }
    }
  }

  async upsertJson(path: string, value: unknown, message: string): Promise<void> {
    const bytes = new TextEncoder().encode(JSON.stringify(value, null, 2));
    let sha: string | undefined;
    try {
      const meta = await this.request<GithubContentFile>(this.url(path, `?ref=${encodeURIComponent(this.branch)}`));
      sha = meta.sha;
    } catch (error) {
      if (!(error instanceof GitHubError && error.status === 404)) throw error;
    }
    await this.put(path, bytes, message, sha);
  }

  async bootstrap(): Promise<void> {
    const roots = ["memories/.gitkeep", "Sent/.gitkeep", "Made/.gitkeep", "Conversation/.gitkeep"];
    const statuses = await Promise.all(roots.map(async (path) => ({ path, exists: await this.exists(path) })));
    for (const item of statuses) {
      if (!item.exists) await this.put(item.path, new TextEncoder().encode(""), `Initialize ${item.path}`);
    }
  }

  rawContentsUrl(path: string): string {
    return `${this.base}/repos/${encodeURIComponent(this.owner)}/${encodeURIComponent(this.repo)}/contents/${path.split("/").map(encodeURIComponent).join("/")}?ref=${encodeURIComponent(this.branch)}`;
  }
}
