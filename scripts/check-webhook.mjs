const token = process.env.TELEGRAM_BOT_TOKEN;
if (!token) {
  console.error("Set TELEGRAM_BOT_TOKEN first.");
  process.exit(1);
}

const response = await fetch(`https://api.telegram.org/bot${token}/getWebhookInfo`);
const text = await response.text();
console.log(text);
if (!response.ok) process.exit(1);
