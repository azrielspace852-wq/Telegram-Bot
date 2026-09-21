const token = process.env.TELEGRAM_BOT_TOKEN;
const webhookUrl = process.env.WEBHOOK_URL;
const secret = process.env.TELEGRAM_WEBHOOK_SECRET;
if (!token || !webhookUrl) {
  console.error("Set TELEGRAM_BOT_TOKEN and WEBHOOK_URL first.");
  process.exit(1);
}
const body = { url: `${webhookUrl.replace(/\/$/, "")}/webhook` };
if (secret) body.secret_token = secret;
const response = await fetch(`https://api.telegram.org/bot${token}/setWebhook`, {
  method: "POST",
  headers: { "content-type": "application/json" },
  body: JSON.stringify(body)
});
console.log(await response.text());
if (!response.ok) process.exit(1);
