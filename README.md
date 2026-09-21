# Neuralis Telegram Bot

Cloudflare Workers Telegram bot for `@Neuralis1_bot` with Gemini AI, GitHub-backed persistence, per-user conversations, long-term memory summarization, file storage, ZIP handling, and slash commands for session/file management.

## Architecture

```text
                        +----------------------+
                        |   Telegram Bot API   |
                        +----------+-----------+
                                   |
                            POST /webhook
                                   v
                         +---------+----------+
                         | Cloudflare Worker  |
                         | fast validation +  |
                         | Queue enqueue      |
                         +---------+----------+
                                   |
                                   v
                        +----------+-----------+
                        | Cloudflare Queues    |
                        | retry + persistence  |
                        +----------+-----------+
                                   |
                                   v
                    +--------------+---------------+
                    | Queue consumer (serialized)  |
                    +------+----------------+-------+
                           |                |
                           v                v
                  +--------+------+ +------+-----------+
                  | Google Gemini | | GitHub REST      |
                  | AI processing | | Database-001     |
                  +---------------+ +------------------+
```

Runtime data is written to the repository using the requested layout:

```text
Database-001/
├── memories/
│   ├── user_<id>.json
│   └── summary_<id>.json
├── Sent/
│   └── <conversation_id>/
├── Made/
│   └── <conversation_id>/
└── Conversation/
    └── <conversation_id>.json
```

Because Git has no empty directories, the Worker creates `.gitkeep` sentinels for `memories/`, `Sent/`, `Made/`, and `Conversation/` on first runtime use.

## Why the webhook was changed

The old version performed GitHub reads/writes and the Gemini request before returning the webhook response. That made the Telegram-facing request depend on every downstream service completing successfully.

The final version returns from `/webhook` after the update is durably handed to Cloudflare Queues. The Queue consumer then performs GitHub/Gemini/Telegram work outside the webhook request path. Queue retries handle transient consumer failures, and a dead-letter queue captures messages that repeatedly fail.

## Reliability protections

Normal chat does not require a slash command. Every user gets an automatically created active conversation.

Queue consumer concurrency is intentionally set to one and batch size to one. This prevents two updates from modifying the same GitHub-backed conversation simultaneously.

Processed Telegram update IDs are retained in per-user memory, and normal text responses use deterministic `model_<update_id>` IDs. If a retry happens after the model response was already stored, the stored response is resent instead of calling Gemini a second time.

The webhook producer returns `503` when queue insertion itself fails. Telegram can then redeliver the update instead of silently losing it.

File upload supports the requested extension set, with filename sanitization, size guards, GitHub persistence, and `/send` and `/discuss` flows. ZIP archives can be generated and safely extracted with a decompression-size cap.

`/generate` supports AI generation for text-oriented formats, PDF, PPTX, ZIP, and images. A legacy binary `.ppt`, real compiled `.apk`, and MP3/MP4/WEBM codec generation are not fabricated by this Worker; existing files in those formats can still be stored and sent. Image generation uses the configured stable Gemini image model and preserves the returned MIME/type.

## Commands

```text
/new [judul]
/conversations
/select <conversation_id>
/files
/generate <format> <prompt>
/send <nama_file>
/discuss <nama_file>
/help
```

## Environment variables

Secrets:

```text
TELEGRAM_BOT_TOKEN
TELEGRAM_WEBHOOK_SECRET
GITHUB_TOKEN
GEMINI_API_KEY
```

Runtime variables are already provided in `wrangler.toml` and can be overridden per environment.

## Deployment

Create the Queue once before deploying:

```bash
npx wrangler queues create telegram-bot-incoming
```

Then follow `DEPLOYMENT.md` for secrets, deployment, webhook setup, and webhook diagnostics.

## Security model

Secrets are only read from Worker bindings. They are never written to GitHub or logs. User-controlled filenames are normalized and reduced to basename-safe values before they are used in repository paths. ZIP extraction strips path components and enforces a total uncompressed-size guard.

A Durable Object is keyed by Telegram `user_id` and enforces a fixed-window request limit. Queue processing is serialized to avoid concurrent conversation writes. The webhook itself only accepts the configured secret and enqueues validated Telegram message updates.

## Platform guardrails

The default file limit is 8 MiB. The Worker allows configuration up to 20 MiB, but the practical ceiling should remain conservative because Workers have a 128 MiB memory limit and GitHub's Contents API accepts file content as Base64.

Cloudflare Queues supports message bodies below 128 KB; Telegram message updates used by this project are metadata/text payloads and remain far below that limit.
