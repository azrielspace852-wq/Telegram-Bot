# Verification Test Cases

## A. Health endpoint

`GET /health` must return JSON with `ok: true`.

## B. Webhook authentication

A request to `POST /webhook` without the configured Telegram secret must return HTTP 403. A matching secret must reach the update parser.

## C. Normal conversation

Send two normal messages without slash commands. The first should create `memories/user_<id>.json` and `Conversation/<id>.json`; the second should append both user/model turns to the same conversation.

## D. New conversation

Run `/new My Test`. Verify that a new conversation JSON file is created and becomes `activeConversationId`.

## E. Conversation selection

Run `/conversations`, copy an ID, then `/select <id>`. Verify `activeConversationId` changes.

## F. File ingestion

Upload a supported document or media file. Verify it appears under `Sent/<conversation_id>/` and `/files` lists it.

## G. File discussion

Run `/discuss <name>`, then send a normal question. Verify Gemini receives the selected file as context when it is within the configured multimodal safety limit.

## H. ZIP extraction

Upload a small ZIP, run `/discuss <zip-name>`, and verify the bot reports archive entries without allowing path traversal.

## I. AI file generation

Test `/generate txt ...`, `/generate json ...`, `/generate pdf ...`, `/generate pptx ...`, `/generate zip ...`, and `/generate png ...`.

## J. Unsupported AI binary generation

Test `/generate apk ...` and `/generate mp4 ...`. The bot should return a user-friendly unsupported-generation message rather than creating a fake binary.

## K. Duplicate update

Replay the same Telegram `update_id`. The bot should not append a duplicate message or create a second stored upload for the same update.

## L. Rate limit

Send more than `RATE_LIMIT_REQUESTS` updates inside the configured window. The bot should send a rate-limit message and reject processing until the window resets.

## M. Error handling

Temporarily use an invalid GitHub token or Gemini API key in staging. The bot should return a user-facing error while logs contain no secret value.
