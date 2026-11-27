# 0017. MCP servers a workspace adds for its agents

- Status: Proposed
- Date: 2026-11-27
- Deciders: owner, Claude
- Changes: ADR 0014's Settings sections (Connectors becomes MCPs, Integrations and Variables; see "Settings")

## Context

Agents need tools beyond ConXist's own (meetings, transcripts). Many tools a team uses already offer an MCP server,
so the first connector is any MCP server, added by its address. The owner walked through five demo versions
(`App/mocks/connectors-v1/` to `connectors-v5/`) and approved v5 (2026-11-27): Settings gets **MCPs** (servers added
by address), **Integrations** (direct APIs, GitHub first) and **Variables** (free-form name and value, replacing
Topics). They are built in that order, one pull request each, and each section shows in the app only once it works.
This record covers MCPs.

## Decision

- **A new `connectors` module.** Its root API is `McpToolbox` (for runs: offer the picked tools, call one). It
  implements the `agents` module's new `AgentMcpTools` port, so `agents` asks whether a pick is allowed without
  knowing about servers. Table `mcp_servers` (V18): name unique per workspace ignoring case, tools as JSON, the
  credential sealed with `SecretBox` (ADR 0004) under a context that names the workspace and the server.
- **Add by address, ConXist finds the sign-in.** `POST /api/mcps/connections` checks the address (https only; no
  loopback, private, link-local, shared or reserved addresses; no user info), then tries the server: if it answers,
  it is public; if it refuses, ConXist reads its OAuth metadata (RFC 9728, RFC 8414), registers a client (RFC 7591)
  and opens the server's own sign-in (PKCE S256, resource indicator RFC 8707); with no OAuth it asks for a token. A
  person can always choose "Use a token instead". The pending connection lives 15 minutes in memory, so a restart
  only means starting again. The sign-in returns to `/api/mcps/callback` (`CONXIST_MCP_REDIRECT_URI`); every OAuth
  endpoint must sit on the issuer's host and pass the same address check, and redirects are never followed.
- **The workspace picks the tools.** After connecting, the server's tools show with its logo (`serverInfo.icons`,
  a data URI or an https image up to 100 KB, else the first letter). Tools marked `readOnlyHint` come ticked; every
  other tool counts as changing things and stays locked until approvals exist (AGENTS.md "Tool calling"). A tool
  that appears later starts off. Up to 20 servers per workspace and 200 tools per server.
- **Whose word "read-only" is.** Only the server says a tool only reads (`readOnlyHint`); MCP tells clients to treat
  that as untrusted, and ConXist cannot check it. The workspace's tick on each tool is the person's approval for
  agents to call it with no further question (TOOL_CALLING_STANDARDS §3), and the screens say plainly that the
  server makes the claim and that what an agent asks a tool goes to that server. Proposed for the owner's approval
  with this record, together with table `mcp_servers` (V18).
- **Agents pick from what is allowed.** An agent definition gains `mcpTools` (server id and tool name, up to 30).
  A pick the workspace no longer allows is the problem `mcp-tool-unavailable` and blocks publishing. A tool an
  agent uses (draft or live, archived agents too) cannot be unticked, and a server whose tools are in use cannot be
  removed; the page names those agents, as with skills (ADR 0013).
- **Test runs call them as data.** The model sees each tool as `<server>__<tool>` (at most 64 characters). Every call
  checks again that the tool is allowed, refreshes an expired token once, and marks the server "Sign in needed"
  when it is refused. Arguments over 4,000 characters are refused before they leave ConXist, and the system prompt
  (version 2026-11-27.1) says to send a server only the few words it needs, never meeting text or other answers.
  Answers are capped at 100,000 characters (errors at 2,000) and wrapped as data like every other tool result; tool
  descriptions from a server are data too. A whole call has 45 seconds, and every answer body 30 seconds to arrive,
  so a slow server can't hold a run. An MCP schema is sent to the model as the server wrote it, without strict mode.
- **Settings.** The sections are General, LLM providers, MCPs, Topics. Topics becomes Variables, and Integrations
  joins, in their own pull requests. Each server has its own page (`#/settings/mcps/{id}`): Check now (lists new
  tools), Sign in again or Replace token, the tool ticks, and Remove.

## Consequences

- Any MCP server's read tools can be used by agents without code per tool. Servers that do not mark tools read-only
  give agents nothing until approvals exist.
- `mvn spring-boot:test-run` runs three fake MCP servers (OAuth, token `mcp-fake-token`, public), all named Acme
  Docs, and allows loopback addresses for them only there.
- Later: share an allowed tool through ConXist's own MCP access (a second tick, off by default), and a list of known
  servers (Jira, GitHub) above the address field.
- REFACTOR LATER, before ConXist is hosted:
  - pin the address a name resolved to (DNS rebinding);
  - an address with a secret in it (a key in the path or query) is stored and shown as typed: seal it, show it masked;
  - untick or flag an allowed tool when its description or schema changes on a later check;
  - check the `iss` the sign-in returns (RFC 9207), and refresh one server's token at a time;
  - in `mvn spring-boot:test-run`, allow only the fake servers' ports instead of any local address;
  - move Plaud's MCP and OAuth client onto the generic classes; reuse MCP sessions instead of opening one per call.
