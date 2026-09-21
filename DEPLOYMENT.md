# Deployment

## 1. Prerequisites

Use Node.js 20+ locally. Install dependencies:

```bash
npm install
```

Authenticate Wrangler:

```bash
npx wrangler login
```

## 2. GitHub token

Create a fine-grained GitHub Personal Access Token scoped only to the `azrielspace852-wq/Database-001` repository with repository `Contents: Read and write` permission.

Do not commit the token into the repository.

## 3. Create the Cloudflare Queue

This version intentionally uses Cloudflare Queues so Telegram webhook requests can return immediately while Gemini/GitHub processing continues in a Queue consumer.

Create the queue once:

```bash
npx wrangler queues create telegram-bot-incoming
```

The configured dead-letter queue `telegram-bot-dead-letter` is created automatically when the consumer configuration is deployed.

The consumer is intentionally configured with `max_batch_size = 1` and `max_concurrency = 1`. This keeps Telegram updates serialized so GitHub-backed conversation state cannot be modified concurrently by multiple queue consumers.

## 4. Create Worker secrets

From the project directory:

```bash
npx wrangler secret put TELEGRAM_BOT_TOKEN
npx wrangler secret put TELEGRAM_WEBHOOK_SECRET
npx wrangler secret put GITHUB_TOKEN
npx wrangler secret put GEMINI_API_KEY
```

For local development, copy `.dev.vars.example` to `.dev.vars` and fill in values.

## 5. Deploy

```bash
npm run typecheck
npm test
npm run deploy
```

Record the deployed Worker URL, for example:

```text
https://neuralis-telegram-bot.<your-subdomain>.workers.dev
```

## 6. Set the Telegram webhook

Linux/macOS:

```bash
export TELEGRAM_BOT_TOKEN='...'
export WEBHOOK_URL='https://neuralis-telegram-bot.<your-subdomain>.workers.dev'
export TELEGRAM_WEBHOOK_SECRET='your-secret'
node scripts/set-webhook.mjs
```

Windows PowerShell:

```powershell
$env:TELEGRAM_BOT_TOKEN='...'
$env:WEBHOOK_URL='https://neuralis-telegram-bot.<your-subdomain>.workers.dev'
$env:TELEGRAM_WEBHOOK_SECRET='your-secret'
node scripts/set-webhook.mjs
```

The webhook endpoint is `/webhook`. The script subscribes only to `message` updates and configures a small webhook connection count because actual processing is handled asynchronously by the Queue.

## 7. Verify webhook health

Run:

```bash
export TELEGRAM_BOT_TOKEN='...'
node scripts/check-webhook.mjs
```

Inspect these fields in the returned JSON when diagnosing delivery problems:

- `url`
- `pending_update_count`
- `last_error_date`
- `last_error_message`
- `max_connections`
- `allowed_updates`

## 8. Smoke test

Open the bot and send:

```text
hello
```

Then:

```text
/new Test Project
/files
/generate md Buat README singkat untuk proyek Neuralis
/conversations
```

Upload a supported file and test:

```text
/files
/discuss nama_file.ext
Jelaskan isi file ini.
/send nama_file.ext
```

## 9. Local worker

```bash
cp .dev.vars.example .dev.vars
# edit .dev.vars
npm run dev
```

The local Worker should also have a Queue binding available in the local Wrangler runtime. Use a public tunnel if Telegram webhook callbacks must reach the local Worker.
