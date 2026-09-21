# Verification Test Cases

## A. Health endpoint

`GET /health` must return JSON with `ok: true`.

## B. Webhook authentication

A request to `POST /webhook` without the configured Telegram secret must return HTTP 403. A matching secret must reach the update parser.

## C. Webhook queue handoff

A valid `message` update must return HTTP 2xx without waiting for Gemini or GitHub. The update must be written to `telegram-bot-incoming` with the same `update_id`.

## D. Queue consumer

The Queue consumer must process the queued update, send the Telegram response, and acknowledge the queue message. Processing failures that prevent the task from completing must use Queue retry handling.

## E. Normal conversation

Send two normal messages without slash commands. The first should create `memories/user_<id>.json` and `Conversation/<id>.json`; the second should append both user/model turns to the same conversation.

## F. New conversation

Run `/new My Test`. Verify that a new conversation JSON file is created and becomes `activeConversationId`.

## G. Conversation selection

Run `/conversations`, copy an ID, then `/select <id>`. Verify `activeConversationId` changes.

## H. File ingestion

Upload a supported document or media file. Verify it appears under `Sent/<conversation_id>/` and `/files` lists it.

## I. File discussion

Run `/discuss <name>`, then send a normal question. Verify Gemini receives the selected file as context when it is within the configured multimodal safety limit.

## J. ZIP extraction

Upload a small ZIP, run `/discuss <zip-name>`, and verify the bot reports archive entries without allowing path traversal.

## K. AI file generation

Test `/generate txt ...`, `/generate json ...`, `/generate pdf ...`, `/generate pptx ...`, `/generate zip ...`, and `/generate png ...`.

## L. Unsupported AI binary generation

Test `/generate apk ...` and `/generate mp4 ...`. The bot should return a user-friendly unsupported-generation message rather than creating a fake binary.

## M. Duplicate update / retry

Replay the same Telegram `update_id`. The bot should not run Gemini twice for the same update. If the model response was already stored but Telegram delivery failed, the retry should resend the stored response instead of generating a second response.

## N. Rate limit

Send more than `RATE_LIMIT_REQUESTS` updates inside the configured window. The bot should send a rate-limit message and reject processing until the window resets.

## O. Error handling

Temporarily use an invalid GitHub token or Gemini API key in staging. The bot should return a user-facing error while logs contain no secret value.

## P. Webhook diagnostics

Run `node scripts/check-webhook.mjs` and verify that `url` points to the deployed `/webhook`, `allowed_updates` contains `message`, and `pending_update_count` does not remain continuously elevated.
