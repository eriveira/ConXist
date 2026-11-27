# 0019. GitHub, the first integration

- Status: Proposed
- Date: 2026-11-27
- Deciders: owner, Claude
- Follows: ADR 0017 (MCP servers), the connectors demo v5 the owner approved on 2026-11-27

## Context

Settings has MCPs (any MCP server, added by address) and Variables. The third section the owner approved is
**Integrations**: tools ConXist reaches through their own API, where ConXist knows each tool, so a person only
connects and chooses. GitHub comes first, with a fine-grained token, read-only (AGENTS.md: repository sources are
read-only by default; repo writes come later as pull requests a person approves).

## Decision

- **In the `connectors` module.** Table `integrations` (V20): one row per workspace and kind (only `GITHUB` now), the
  token's account, the repositories it reaches, the token's last 4 characters, the tools agents may use, and the
  token sealed with `SecretBox` (ADR 0004) under a context that names the workspace and the row.
- **Connect with a fine-grained token.** The dialog explains how to make one for the chosen repositories with
  Contents, Issues, Pull requests and Metadata read-only, and links to GitHub's page. ConXist accepts only tokens that
  start with `github_pat_` (a classic token reaches every repository the person can see). It checks the token with
  GitHub (`GET /user`, `GET /user/repos`, at most 100 repositories), shows the account and repositories, and saves
  nothing until the person picks the tools and presses Add. ConXist cannot read a fine-grained token's permissions,
  so it cannot prove the token is read-only; it only ever sends `GET` requests, and the screen asks for read-only
  access. Proposed for the owner's approval with this record, together with table `integrations` (V20).
- **Five read tools, written by ConXist.** `search_code`, `get_file`, `list_issues`, `get_issue`,
  `get_pull_request`. Their names, descriptions and input schemas are ConXist's own, so they are not untrusted text
  like an MCP server's. Every call names a repository the token was checked for (or, for `search_code`, searches all
  of them), so an agent never reaches a repository the integration does not list. Proposing a pull request comes once
  approvals exist.
- **Agents use them like MCP tools.** An agent's pick is the integration's id and the tool's name, the same
  `mcpTools` list ADR 0017 added, so publishing checks, "in use" checks and test runs work the same way. The model
  sees `github__<tool>`. What GitHub answers (code, issues, comments) is customer data: wrapped as data in the run,
  cut to 100,000 characters, never logged.
- **Settings › Integrations** lists GitHub once connected (account, repositories, token ending, tools allowed, who
  uses them). Its page (`#/settings/integrations/{id}`) has Check now (the repositories again), Replace token, the
  tool ticks, and Remove. A tool agents use can't be unticked and a used integration can't be removed; the page names
  those agents, as with MCPs. Add shows only while an integration can still be added (GitHub is the only one).
- `mvn spring-boot:test-run` runs a fake GitHub API that takes any token starting with `github_pat_fake`.

## Consequences

- Agents can read code, issues and pull requests in a workspace's chosen repositories in test runs.
- REFACTOR LATER: a token with write access still works, because GitHub does not tell ConXist what a fine-grained
  token may do; GitHub Apps (installation tokens with read-only permissions ConXist asks for) would let ConXist
  prove it, and suit a hosted ConXist better than personal tokens. Code search reads the default branch only, and
  the `repo:` list of a search has to fit GitHub's 256-character query, so with many repositories the model is asked
  to name one.
- Later: Bitbucket (Phase 1 scope), and other integrations, each in its own pull request.
