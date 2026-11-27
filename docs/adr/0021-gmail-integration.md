# 0021. Gmail, read by agents

- Status: Proposed
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
  address shortened (`al…@acme.test`), so no full address is kept in the clear. One Gmail per workspace.
- **Sign in with Google, read-only Gmail only.** ConXist uses its own Google OAuth client, an authorization code with
  PKCE (S256), `access_type=offline` and `prompt=consent`, and asks only for
  `https://www.googleapis.com/auth/gmail.readonly`. If Google's answer grants anything else, the sign-in is refused and
  nothing is kept. Google's page opens in a new window; Google sends it back to
  `/api/integrations/gmail/callback`, which shows a fixed page. Sign-ins in progress live in memory for 15 minutes
  (as MCP sign-ins do, ADR 0017), and the refresh token is stored only when the person adds Gmail. Access tokens live
  in memory only. "Sign in again" replaces the refresh token and keeps the labels.
- **Only with a client.** Gmail is offered (`GET /api/integrations/kinds`) only when `CONXIST_GOOGLE_CLIENT_ID` and
  `CONXIST_GOOGLE_CLIENT_SECRET` are set, so the app shows only what works. The client's redirect URI must be
  `CONXIST_GOOGLE_REDIRECT_URI` (default `http://127.0.0.1:8080/api/integrations/gmail/callback`).
- **The person picks what agents may read.** Only emails with some labels (recommended), or all email but Spam and
  Trash (stored as the single value `*`). Labels are the mailbox's own, by name; they can be changed on the
  integration's page. A mailbox with no labels yet can make one in Gmail and check again.
- **Four read tools, written by ConXist.** `search_emails` (Gmail search words, up to 25, newest first, sender,
  subject, date and Gmail's preview), `get_email` (headers and text; HTML turned to text; attachments by name only,
  never opened; 20,000 characters), `get_thread` (up to 50 emails, 4,000 characters each), `list_labels`.
  Each search runs inside one allowed label at a time (Gmail's `labelIds`), words starting with `in:` are dropped,
  and every email is checked to carry an allowed label and neither SPAM nor TRASH; one that doesn't answers as
  missing. GET only, no redirects. Emails are customer data and untrusted input, handled as GitHub's and
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
- REFACTOR LATER: Gmail as a capture source; revoke on remove once sign-ins are per person; a shared mailbox through a
  Google Workspace service account; search several labels in one call (Gmail's `labelIds` means all of them); more than
  100 labels.
