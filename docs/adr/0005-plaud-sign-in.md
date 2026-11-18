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

1. **Personal accounts sign in on Plaud's own page.** ConXist registers itself as
   a public OAuth client (no secret) the first time, starts a sign-in with a
   fresh `state` and PKCE verifier, and opens Plaud's page in a new window. The
   window comes back to `GET /api/recorders/plaud/callback`, which shows a fixed
   page and closes itself; the dialog asks `GET /api/recorders/plaud/sign-ins/{id}`
   every two seconds. A sign-in that is not finished in 15 minutes fails.
2. **ConXist never sees the Plaud password.** It keeps only the access and
   refresh tokens, sealed with `CONXIST_SECRETS_KEY` (AES-256-GCM, the source id
   as associated data, ADR 0004). The PKCE verifier is sealed the same way until
   the sign-in ends. Tokens are refreshed a minute before they expire and once
   more if Plaud refuses one.
3. **Read through Plaud's MCP tools, behind the `PlaudAccounts` port.** The
   adapter (`capture.adapter.plaudaccount`) speaks MCP Streamable HTTP. Each
   recording is imported once (by Plaud file id), with its transcript and, when
   chosen, Plaud's summaries. Recordings still being transcribed are counted and
   picked up by a later sync. Recording content from Plaud is customer data:
   never instructions, never logged.
4. **Signed out means paused.** If Plaud refuses the tokens for good, the source
   pauses and its card offers "Sign in again". Signing in again must use the same
   account, so one source never mixes two people's meetings; meetings already
   imported stay.
5. **Preview, and ask Plaud.** The source is marked Preview in the app and on the
   product page, and we ask Plaud (dev.plaud.ai) to confirm a third-party server
   app may use this sign-in. If Plaud says no, the personal door goes back to
   "coming soon" and imported meetings stay.

## Consequences

- The redirect address is the API's own (`127.0.0.1` while there is no hosting).
  A hosted ConXist will need its public callback URL; registration is per
  redirect address, so that needs no change in code.
- Plaud's MCP tools do not give a recording count, so the dialog shows the
  account's email only (the mock also showed a count).
- `list_files` dates are local times with no zone; ConXist reads them as UTC and
  reads three days back on each sync, so a recording is never missed at a day
  boundary.
- If Plaud changes its MCP tools, only the adapter changes.
