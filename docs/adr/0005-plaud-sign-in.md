# 0005. Sign in with Plaud for personal accounts

- Status: **Accepted** (preview until Plaud confirms outside apps may use its sign-in)
- Date: 2026-11-18
- Decided by: Eugenio Riviera (decision card "Build Sign in with Plaud", project thread, 2026-11-18; sign-in screens approved in the Plaud connect mock)

## Context

The owner's Plaud account is personal, so the Enterprise API of ADR 0003 cannot
read it. Plaud's personal OAuth API is a closed beta limited to the 20 most
recent recordings. Plaud's hosted MCP server (`https://mcp.plaud.ai/mcp`) is
free for personal accounts and uses standard OAuth 2.1: authorization-server
metadata (RFC 8414), open dynamic client registration (RFC 7591), PKCE S256,
the `resource` parameter (RFC 8707) and refresh tokens. Its tools
(`get_current_user`, `list_files`, `get_transcript`, `get_note`) are read-only.
Plaud's terms do not say whether a third-party server application may use this
sign-in; the directory clients that use it today are AI apps.

## Decision

1. **Personal accounts sign in on Plaud's own page.** ConXist finds the
   authorization server the MCP server names (RFC 9728 protected-resource
   metadata, or else the MCP server's own host), reads its metadata (RFC 8414,
   endpoints must be https and on that server), registers itself as a public
   OAuth client (no secret) the first time, registering again if the server
   forgets it, starts a sign-in with a
   fresh `state` and PKCE verifier, and opens Plaud's page in a new window. The
   window comes back to `GET /api/recorders/plaud/callback`, which shows a fixed
   page and closes itself; the dialog asks `GET /api/recorders/plaud/sign-ins/{id}`
   every two seconds. A sign-in that is not finished in 15 minutes fails, and one
   finished but not given to a source within an hour is dropped with its tokens.
2. **ConXist never sees the Plaud password.** It keeps only the access and
   refresh tokens, sealed with `CONXIST_SECRETS_KEY` (AES-256-GCM, the source id
   as associated data, ADR 0004). The PKCE verifier is sealed the same way until
   the sign-in ends. Tokens are refreshed a minute before they expire and once
   more if Plaud refuses one, one refresh per source at a time (Plaud rotates
   refresh tokens). Plaud's sign-in has no read-only scope to ask for: the
   tokens are limited to the MCP server (RFC 8707 `resource`), and ConXist calls
   only the four read tools by name and never reads the server's tool list.
3. **Read through Plaud's MCP tools, behind the `PlaudAccounts` port.** The
   adapter (`capture.adapter.plaudaccount`) speaks MCP Streamable HTTP. Each
   recording is imported once (by Plaud file id), with its transcript and, when
   chosen, Plaud's summaries. Recordings still being transcribed are counted and
   picked up by a later sync: each sync looks again at the last 30 days, because
   a recorder may upload days after the meeting. Answers are read up to a size
   limit, and a transcript stops being read past what ConXist keeps. Recording content from Plaud is customer data:
   never instructions, never logged.
4. **Signed out means paused.** If Plaud refuses the tokens for good, the source
   pauses and its card offers "Sign in again". Signing in again must use the same
   account, so one source never mixes two people's meetings; meetings already
   imported stay. "Same account" is decided by Plaud's user id (stored only as a
   SHA-256 key), not by the email; a sign-in without a user id fails.
5. **Preview, and ask Plaud.** The source is marked Preview in the app and on the
   product page, and we ask Plaud (dev.plaud.ai) to confirm a third-party server
   app may use this sign-in. If Plaud says no, the personal door goes back to
   "coming soon" and imported meetings stay.

## Data

Migration `V4__plaud_accounts.sql` adds four tables. It needs the owner's go
before it is applied anywhere but a developer's computer.

| Table | Holds | Kept |
|---|---|---|
| `oauth_clients` | The client id each authorization server gave ConXist, per redirect address | Until the server forgets it |
| `plaud_sign_ins` | One sign-in: `state`, sealed PKCE verifier, status, account email and key, sealed tokens until used | Deleted an hour after it started |
| `plaud_account_connections` | The source's link to the account: email, account key, start choice, last sync | While the source exists |
| `plaud_account_tokens` | The account's current tokens, sealed | While the source exists; replaced on every refresh |

## Consequences

- The redirect address is the API's own (`127.0.0.1` while there is no hosting).
  A hosted ConXist will need its public callback URL; registration is per
  redirect address, so that needs no change in code.
- Plaud's MCP tools do not give a recording count, so the dialog shows the
  account's email only (the mock also showed a count).
- `list_files` dates are local times with no zone; ConXist reads them as UTC, so
  the "last 7 days" cutoff can be off by up to a day for people far from UTC.
  Plaud gives no time zone to correct it with.
- There is no disconnect yet, so ConXist never revokes a Plaud sign-in; a person
  can remove ConXist's access from their Plaud account settings.
- If Plaud changes its MCP tools, only the adapter changes.
