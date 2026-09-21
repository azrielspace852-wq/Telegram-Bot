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

## 3. Create Worker secrets

From the project directory:

```bash
npx wrangler secret put TELEGRAM_BOT_TOKEN
npx wrangler secret put TELEGRAM_WEBHOOK_SECRET
npx wrangler secret put GITHUB_TOKEN
npx wrangler secret put GEMINI_API_KEY
```

For local development, copy `.dev.vars.example` to `.dev.vars` and fill in values.

## 4. Configure `wrangler.toml`

The repository name and owner are already set. Change only the Worker name or variables when needed. `RATE_LIMITER` uses a new SQLite-backed Durable Object migration.

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

The webhook endpoint is `/webhook` and validates `X-Telegram-Bot-Api-Secret-Token` when `TELEGRAM_WEBHOOK_SECRET` is configured.

## 7. Smoke test

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

## 8. Local worker

```bash
cp .dev.vars.example .dev.vars
# edit .dev.vars
npm run dev
```

Use a public tunnel if Telegram webhook callbacks must reach a local Worker. Alternatively deploy a staging Worker and point the webhook at the staging URL.
