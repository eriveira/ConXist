# 0003. Plaud Team workspaces as the first real recorder source

- Status: **Accepted**
- Date: 2026-11-18
- Decided by: Eugenio Riviera (decision card "Both, Enterprise first"; connect-flow and Sources page mocks approved in the project thread, 2026-11-18)

## Context

Slice 1 showed Plaud as a preview that signed in to a sample account. The owner
asked for Plaud to become the first real source. Plaud offers four ways in
(researched 2026-11-18 on docs.plaud.ai):

| Path | Fit |
|---|---|
| **Enterprise API** (`api.plaud.ai/enterprise/v1`) | Official, server-to-server, read-only scopes by default, change feed with checkpoints. Team workspaces only. |
| Personal OAuth API | Closed beta, 20 most recent recordings only. Not viable. |
| Plaud MCP (personal sign-in) | Free for personal accounts, OAuth in the browser. Built for AI clients; third-party server use is not stated. |
| Embedded SDK / Transcription API | For device makers, not for reading a user's recordings. |

## Decision

1. **Build the Enterprise API path now** for Plaud Team workspaces, behind the
   `PlaudTeam` port in `capture.application`. The personal path ("Sign in with
   Plaud", likely through Plaud MCP) is a later slice and shows as coming soon.
   (Since built: see ADR 0005.)
2. **ConXist makes the key pair.** Each connection gets an RSA 2048 key
   (`cx-` + 16 hex). The public key is shown to copy to the Plaud Team admin;
   the private key is sealed with AES-256-GCM (key id as associated data) and
   never leaves the API. The sealing key comes from `CONXIST_SECRETS_KEY`, which
   has no default; the API does not start without it.
3. **Sign in with `private_key_jwt`.** Client assertions are RS256, live 120 s
   and carry a one-time `jti`. Access tokens (1 h) are cached per credential and
   renewed 60 s early; a stale-token 401 is retried once.
4. **Read the change feed.** A sync reads `/team-files/items` page by page and
   saves the new checkpoint only after the last page, so a failure re-reads the
   same window. `410 checkpoint_expired` re-reads from the start; documents are
   de-duplicated by Plaud file id, so a re-read imports nothing twice.
5. **What is imported.** One Markdown document per meeting: title, date,
   language, Plaud's summaries when the person chose them, and the transcript
   with speaker and time. Audio is never copied. Meetings before the chosen
   start (last 30 days, last 7 days, or only new) are skipped; meetings Plaud
   has not transcribed yet are counted as waiting and picked up later.
6. **When it runs.** Every `conxist.plaud.sync-interval` (5 minutes by default)
   for active connections, and on Sync. If Plaud stops accepting the key, the
   connection is paused and the card says so; Retry resumes it once access is
   back.
7. **Errors stay coarse.** An unknown Client ID and an unregistered key look the
   same to Plaud, so both read as "Plaud did not accept this Client ID and key".
   Logs carry status and error code only, never transcript text or keys.
8. **The canvas becomes a Sources page.** With the hub hidden (2026-11-17), the
   canvas only showed cards. The approved mock replaces it with a page of
   source cards, and React Flow is removed.

## Consequences

- Plaud shows as **Preview** in the app and on the product page until one real
  Plaud Team signs in. The code path is the real one; only the fake Plaud has
  answered it so far.
- Imported meeting text is encrypted at rest since ADR 0004.
- Untrusted transcript text is kept to one line per utterance, speakers are
  cleaned, summaries are quoted, and meetings stop at
  `conxist.capture.max-text-characters`, so spoken text cannot forge document
  structure that later steps or agents would trust. No MCP or agent tool reads
  these documents yet.
- A refusal on one recording (a member's private file) skips that recording; only
  a refusal on the workspace or the feed pauses the connection. One failing Team
  never stops the scheduled sync of the others.

- Live use needs a Plaud Team Client ID from Plaud, which Plaud's docs do not
  explain how to get. The token `aud` value is configurable
  (`CONXIST_PLAUD_TOKEN_AUDIENCE`) because the docs only name it "the issuer
  string Plaud gives".
- Members' private recordings are only readable if they opted in on Plaud's
  side; ConXist cannot see that count, so the dialog explains it instead.
- `mvn spring-boot:test-run` starts a fake Plaud (Client ID `demo-team`) so the
  whole flow can be tried without a Plaud account.
- Migration `V2__plaud_team.sql` adds `recorder_keys` and
  `plaud_team_connections`. Applying it anywhere shared needs the owner's go.
