# 0021. Gmail, read by agents

- Status: Superseded by ADR 0027 (owner, 2026-11-28)
- Date: 2026-11-27
- Deciders: owner, Claude
- Follows: ADR 0019 (GitHub), ADR 0020 (Bitbucket), the connectors demo v6 the owner approved on 2026-11-27 ("good")

## Context

The owner asked for Bitbucket and Gmail (2026-11-27). Gmail could be a capture source (emails become meetings and
requirements) or an integration whose tools agents call. The demo showed it as an integration, the owner approved the
demo, and a decision card offering source, integration or both was not answered before Bitbucket merged; this records
the integration, the recommended option. A capture source can follow and reuse the same sign-in.

A mailbox is personal and much more sensitive than a repository, and Google signs in only with OAuth: there is no
read-only token to paste.

## Decision

- **Same table, same module.** `integrations` gains the kind `GMAIL` (V22 changes only the kind check). The sealed
  credential is the refresh token of the person's Google sign-in (ADR 0004). The account shown is the mailbox's
  address shortened (`al…@acme.test`), so no full address is kept in the clear. One Gmail per workspace. In place of
  a token's last 4 characters, Gmail keeps the mailbox's mark: 4 hex characters of the SHA-256 of its address, so no
  part of the refresh token is kept in the clear.
- **Sign in with Google, read-only Gmail only.** ConXist uses its own Google OAuth client, an authorization code with
  PKCE (S256), `access_type=offline` and `prompt=consent`, and asks only for
  `https://www.googleapis.com/auth/gmail.readonly`. If Google's answer grants anything else, the sign-in is refused and
  nothing is kept. Google's page opens in a new window; Google sends it back to
  `/api/integrations/gmail/callback`, which shows a fixed page. Sign-ins in progress live in memory for 15 minutes
  (as MCP sign-ins do, ADR 0017), and the refresh token is stored only when the person adds Gmail. Access tokens live
  in memory only. "Sign in again" replaces the refresh token and keeps the labels, only for the same mailbox: a sign-in
  whose mailbox mark differs is refused (`integration.gmail-other-account`) and nothing changes, so picking the wrong
  account in Google's chooser can't switch every agent to another mailbox (1 in 65,536 other mailboxes could match;
  this guards against a slip, not an attacker who already has the workspace). A sign-in that fails for any reason
  ends as failed at once, logged with the workspace and the reason only.
- **Only with a client.** Gmail is offered (`GET /api/integrations/kinds`) only when `CONXIST_GOOGLE_CLIENT_ID` and
  `CONXIST_GOOGLE_CLIENT_SECRET` are set, so the app shows only what works. The client's redirect URI must be
  `CONXIST_GOOGLE_REDIRECT_URI` (default `http://127.0.0.1:8080/api/integrations/gmail/callback`).
- **The person picks what agents may read.** Only emails with some labels (recommended), or all email but Spam and
  Trash (stored as the single value `*`). Labels are the mailbox's own, by name; they can be changed on the
  integration's page. A mailbox with no labels yet can make one in Gmail and check again.
- **Four read tools, written by ConXist.** `search_emails` (Gmail search words, up to 25, newest first, sender,
  subject, date and Gmail's preview), `get_email` (headers and text; HTML turned to text; attachments by name only,
  never opened; 20,000 characters), `get_thread` (up to 50 emails, 4,000 characters each), `list_labels`.
  Each search runs inside one allowed label at a time (Gmail's `labelIds`), at most 20 labels (the answer says how
  many were not searched), reading at most 50 emails, the newest of each label in turn; words starting with `in:` are
  dropped,
  and every email is checked to carry an allowed label and neither SPAM nor TRASH; one that doesn't answers as
  missing. With all mail, `list_labels` lists the person's own labels, never Spam or Trash. HTML becomes text in one
  forward pass over at most 400,000 characters, so a hostile email can't make it slow. GET only, no redirects. Emails are customer data and untrusted input, handled as GitHub's and
  Bitbucket's answers are: never instructions, never logged.
- The connect dialog says plainly that this is the person's own mailbox and that every agent in the workspace given
  Gmail tools can read the picked labels.
- `mvn spring-boot:test-run` runs a fake Google that signs in at once as `alex@acme.test`, with labels Customers,
  Product feedback and Personal and one email in Spam.

## Consequences

- An agent can search and read the emails in the labels the person picked, in a test run.
- Before the owner can use real Gmail, someone makes a Google Cloud OAuth client: a project with the Gmail API on,
  an OAuth consent screen in Testing with the owner as a test user (`gmail.readonly` is a restricted scope; unverified
  apps are limited to test users), a Web client with the redirect URI above, and its id and secret in the environment.
  Hosting ConXist for customers needs Google's verification and security assessment for that scope.
- Not yet verified against Google itself, only against the fake.
- Removing Gmail forgets the refresh token but does not revoke it at Google: revoking ends every sign-in of that
  person with ConXist's client, including other workspaces. The person can remove ConXist's access in their Google
  account.
- BLOCKER before hosting: tie a sign-in's state to the person who started it (a session or the signed-in user), so a
  Google link sent to someone else can't land their mailbox in another workspace. Today the redirect goes to the
  person's own 127.0.0.1.
- REFACTOR LATER: keep labels by Gmail's id as well as by name (a label renamed and another given its name would be
  read); a cap of sign-ins in progress per workspace; revoke at Google a refresh token that was exchanged but never
  stored; one store for sign-ins in progress shared with MCPs, and one PKCE helper with Plaud and MCPs; a per-kind
  policy instead of `GMAIL` branches; a cursor for `search_emails`; the tool-calling eval set for all integration
  tools; Google's own "G" button once the owner and Branding approve it. Gmail as a capture source; revoke on remove once sign-ins are per person; a shared mailbox through a
  Google Workspace service account; search several labels in one call (Gmail's `labelIds` means all of them); more than
  100 labels.
