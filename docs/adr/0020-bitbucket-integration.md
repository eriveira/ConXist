# 0020. Bitbucket, the second integration

- Status: Proposed
- Date: 2026-11-27
- Deciders: owner, Claude
- Follows: ADR 0019 (GitHub), the connectors demo v6 the owner approved on 2026-11-27 ("good")

## Context

The owner asked for Bitbucket and Gmail next (2026-11-27). Bitbucket is in the Phase 1 repository scope (AGENTS.md).
It works like GitHub, with two differences:

- Bitbucket Cloud's personal credential is an **Atlassian API token** (Bitbucket's app passwords are being retired),
  sent with the person's Atlassian email. It has scopes but no repository list: it reaches every repository its
  person can see.
- Some reads answer with a redirect on Bitbucket's own API (a pull request's changes).

Gmail is a separate decision and pull request.

## Decision

- **Same table, same module.** `integrations` gains the kind `BITBUCKET` (V21 changes only the kind check). The sealed
  credential holds the email and the API token together, under the same per-row context (ADR 0004). The account
  shown is the person's Bitbucket name, never the email, so the email is stored only sealed. Only the token's last 4
  characters show. One Bitbucket per workspace, as for GitHub.
- **Integration kinds carry their own catalogue.** `Integration.Kind` names each service, its tools and how it
  writes a repository. One `IntegrationApi` port, with one adapter per service, replaces the GitHub-only port. This
  settles two REFACTOR LATER items from ADR 0019 (one tool catalogue per kind, one place per service). Error codes
  that name a service start with its kind, such as `integration.bitbucket-token-refused`, so the app can say which
  service; `integration.in-use` became `integration.github-in-use` and `integration.bitbucket-in-use`.
- **Connect with an email and an API token.** The dialog explains how to create an API token with scopes in the
  Atlassian account, choosing Bitbucket and only `read:repository:bitbucket`, `read:pullrequest:bitbucket`,
  `read:workspace:bitbucket` and `read:user:bitbucket`.
  ConXist accepts only tokens that start with `ATATT`. It checks them with Bitbucket (`GET /user`,
  `GET /repositories?role=member`, at most 100 repositories) and saves nothing until Add.
- **The person picks the repositories.** Because the token reaches everything its person can see, the second step
  lists the repositories it reaches and the person ticks the ones agents may read (at least one). Only those are
  stored, and every tool call names one of them. Check now and Replace token keep the picked ones the token still
  reaches; when Bitbucket's list was cut at 100, all picks stay, and a check or new token that reaches none of them
  changes nothing and says so. The integration's page can change the pick (`PUT /api/integrations/{id}/repositories`, with the list from
  `POST /api/integrations/{id}/reachable`). GitHub's repositories are still picked on GitHub.
- **Five read tools, written by ConXist.** `search_code` (one repository, default branch, needs Bitbucket's code
  search on in the workspace), `get_file`, `list_pull_requests`, `get_pull_request` (text, up to 50 comments, the
  changes cut at 20,000 characters), `list_commits`. Searches drop search syntax as GitHub's do, and also quotes
  and any word with a single colon; the repository comes first in the query (`repo:slug words`). Bitbucket searches
  a whole workspace, so ConXist keeps only hits whose own address is in the picked repository. A commit's author
  shows by name, never by email. Bitbucket's answers are customer data, handled exactly as GitHub's.
- **GET only, redirects on Bitbucket's own address only.** The HTTP client never follows redirects by itself. The
  adapter follows one redirect by hand, and only when it stays under the configured API address, so the credential
  never reaches another host.
- **Settings › Integrations** gets an Add menu listing what can be added (GitHub, Bitbucket); a connected one shows
  as connected. The app shows only what works, so Gmail is not listed until it works.
- `mvn spring-boot:test-run` runs a fake Bitbucket API that takes any email with a token starting with `ATATTfake`.

## Consequences

- An agent can read Bitbucket code and pull requests in a test run, from the repositories the person picked.
- ConXist cannot prove the token is read-only. The dialog asks for read scopes only, and ConXist only sends GET.
  An unscoped Atlassian API token also starts with `ATATT` and carries the person's whole Atlassian access
  (Bitbucket writes, Jira, Confluence); ConXist would seal it like any other. This is an accepted risk for the
  owner to confirm: check against real Bitbucket whether a token's scopes can be read, and refuse unscoped or
  write-scoped tokens if they can.
- A service refusing a request (400, 403, 422, 429) says so (`integration.<kind>-refused`, a check's `refused`)
  instead of "try again in a minute", since a missing scope is the likeliest cause.
- Not yet verified against Bitbucket itself, only against the fake. Before the owner relies on it, connect a real
  token once: the scope names in the dialog and the code search answer's shape are the likely places to differ.
- REFACTOR LATER: show "first 100" when a token reaches more repositories, and page through them; search across
  several picked repositories in one call; follow a redirect only within the call's own repository; require https
  for the API addresses; refuse binary files in `get_file`; Bitbucket workspace access tokens (Bearer, no person's email) for teams;
  OAuth sign-in when ConXist is hosted.
