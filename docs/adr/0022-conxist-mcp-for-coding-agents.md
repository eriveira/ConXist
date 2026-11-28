# 0022. ConXist's own MCP server for coding agents

- Status: Proposed
- Date: 2026-11-27
- Deciders: owner, Claude
- Follows: ADR 0002 (the "MCP server" row: Spring AI, its own module), ADR 0017, 0019 to 0021 (the tools a workspace
  allows), ADR 0010 and 0011 (published agents and skills)
- Mock: `App/mocks/coding-agents-mcp-v1/` (Settings › Coding agents)
- Bundle format: `App/specs/conxist-bundle-v1/conxist-bundle.md` (thread "Redesign agent creation and setup")

## Context

The owner (2026-11-27): "Coding agent should interact with ConXist MCP, this MCP will be able to proxy allowed tools,
Spring AI will be able to do this. Also changes on the agents and skills configurations need to be able to take
effect at runtime."

A workspace already allows tools for its own agents: ConXist's three read tools, its MCP servers' tools and its
integrations' tools (GitHub, Bitbucket, Gmail). It also publishes agents and skills. Coding agents (Claude Code,
Codex, any MCP client) can't reach any of it. ConXist has no sign-in yet and listens on 127.0.0.1 only.

## Decision

- **One address, one module.** A new Modulith module `mcp` (the name ADR 0002 gave it) serves MCP over Streamable
  HTTP at `/mcp`, built on Spring AI's MCP server starter (`spring-ai-starter-mcp-server-webmvc`, Spring AI 2.0, which
  targets Spring Boot 4). It calls other modules only through their root APIs: `connectors.McpToolbox` for proxied
  tools, `runs` for running an agent, `agents` for published skills. LLM calls stay on the Anthropic and OpenAI Java
  SDKs; Spring AI is added for the MCP server only.
- **Each workspace sees its own list.** Spring AI's server holds one tool list, so ConXist builds one MCP server
  per workspace on first use (the SDK's `McpSyncServer`) and routes each request by the workspace its token or key names. The first
  pull request starts with a spike that proves this, and falls back to the MCP Java SDK directly if the starter
  can't host several servers.
- **Only signed-in people, only their workspaces** (owner, 2026-11-28: "The ConXist MCP needs to be able to allow
  only authorized user to interact with authorized Workspaces"). ConXist has no sign-in yet, so this needs people and
  workspace members first (ADR 0002 "Open": auth provider). Proposed:
  - *People and members* come first (ADR 0023, its own pull request): a person signs in to ConXist; each
    workspace has members with a role (Owner, Member). Settings › Coding agents and its switches are for Owners.
  - *The MCP signs people in with MCP's own OAuth flow.* ConXist is the authorization server; a client like
    Claude Code finds it from `/.well-known/oauth-protected-resource` (RFC 9728), registers (RFC 7591), and opens
    ConXist's sign-in in the browser (PKCE S256, resource indicator RFC 8707 for `/mcp`). The person then picks
    one of *their* workspaces. The token names the person and that one workspace, lives one hour, and refreshes.
  - *One connection, one workspace* (owner, 2026-11-28: "every workspace brings its own tools"). A connection
    sees only its workspace's shared tools, agents, skills and bundle, never a mix. Using a second workspace means
    adding a second connection (for example `conxist-acme` and `conxist-personal`), each with its own sign-in.
  - *Every request checks again*: the person is still a member of that workspace, and the item is still shared.
    Leaving a workspace, or losing access, cuts it off on the next call; open sessions for it are closed.
  - *Access keys* stay for machines with no browser (CI, a build box): a key belongs to one person and one
    workspace, is made by that person, stops working when their membership ends, and is listed on the Access
    keys tab with its owner. Stored as SHA-256 and last 4 characters (table `mcp_access_keys`).
  - Until sign-in exists, the MCP is not built.
- **Nothing is shared until a person turns it on.** Settings › Coding agents › Shared lists, grouped by where they
  come from: ConXist's tools, each MCP server's and integration's tools *that the workspace already allows*, published
  agents, and published skills. Each has a switch, off by default (table `mcp_shares`: workspace, kind, reference).
  A tool that changes things can be shared only after a warning that names the service and says the switch is the
  approval for every call (the owner's write-tool rule of 2026-11-27, applied here too). A tool the workspace stops
  allowing stops being shared.
- **What a coding agent gets.**
  - *Tools*: each shared tool, named as ConXist's agents see it (`<server>__<tool>`, for example
    `github__search_code`; ConXist's own keep their plain name, `get_transcript`), so a coding agent sees
    `mcp__conxist__github__search_code`. Calls go through `McpToolbox` exactly as test runs do: allowed checked again
    on every call, the same size and time caps, the workspace's own credential. Results go back as tool results; the
    coding agent's model reads them as data.
  - *The bundle*: shared published agents and skills, in ConXist's provider-neutral format
    (`App/specs/conxist-bundle-v1/conxist-bundle.md`): agents as Markdown with a `conxist:` block (version, effort,
    skills, tools), skills as SKILL.md. It is served as the MCP resource `conxist://bundle` (and
    `GET /api/coding-agents/bundle` with the same key). A ConXist plugin for Claude Code or Codex reads it and writes
    each tool's native files (a subagent, a skill); the agent then runs inside the coding agent, on its own model,
    and reaches tools only through this server. Variables are filled in; secrets never go in a bundle. Drafts never
    go out.
  - *Skills as prompts* too: each shared skill is also an MCP prompt that returns its live SKILL.md, so a client
    without the plugin can still use it (Claude Code shows `/mcp__conxist__bug-report`).
  - Later, if the owner wants it: `ask_<agent>`, a tool that runs a published agent inside ConXist on the
    workspace's LLM key. Not in the first pull request.
- **Changes take effect at once.** Publishing an agent or skill, restoring a version, archiving or deleting one,
  switching a share, unticking an allowed tool, removing a server or turning off a key each raise a Modulith event.
  The `mcp` module listens and updates that workspace's server, which sends `notifications/tools/list_changed`,
  `notifications/prompts/list_changed` or `notifications/resources/updated` (for `conxist://bundle`) to its open
  sessions; the plugin then fetches the bundle again and replaces the files whose `version` changed. Nothing restarts. A call always reads the current state,
  so even a client that ignores the notification can't call something no longer shared. ConXist's own agents already
  read their definition on every run; this record adds nothing there.
- **Guards.** The endpoint checks the `Origin` header (MCP's rule against DNS rebinding for local servers) and
  refuses a request with no valid key. Each key may make 60 calls a minute. Every call is
  logged with tool, key name, workspace, time taken and outcome, never arguments or results; the Access keys tab shows
  last use and calls today.

## Consequences

- A coding agent can read meetings, code, issues and email the workspace allows, and run the team's published agents
  and skills as its own, with one line of setup, and sees changes without reconnecting.
- A shared tool runs on the workspace's sign-in, not the caller's, so every member a workspace lets in gets that
  reach; the screens say so. Sign-in and members come first, which moves the MCP after that work.
- The product page's "MCP access" line moves from coming soon to available once this ships (not before).
- Codex reads tool list changes when it next lists tools; whether it acts on `list_changed` mid-session is to be
  checked during the build.
- REFACTOR LATER: shares per person or role, if the owner wants one member to see less than another; per-person
  rate limits when ConXist is hosted.
