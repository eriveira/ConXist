# 0022. ConXist's own MCP server for coding agents

- Status: Proposed
- Date: 2026-11-27
- Deciders: owner, Claude
- Follows: ADR 0002 (the "MCP server" row: Spring AI, its own module), ADR 0017, 0019 to 0021 (the tools a workspace
  allows), ADR 0010 and 0011 (published agents and skills)
- Mock: `App/mocks/coding-agents-mcp-v1/` (Settings › Coding agents)

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
  per workspace on first use (the SDK's `McpSyncServer`) and routes each request by its access key. The first
  pull request starts with a spike that proves this, and falls back to the MCP Java SDK directly if the starter
  can't host several servers.
- **Sign-in: an access key per coding agent.** Settings › Coding agents › Create access key makes a key
  (`cxk_live_` and 32 random characters), shown once. ConXist keeps only its SHA-256 and last 4 characters (table
  `mcp_access_keys`, next migration: workspace, name, hash, last 4, created, last used, turned off). The client sends
  it as `Authorization: Bearer`. A key reaches one workspace. Turning a key off ends its open sessions. When sign-in
  exists, the MCP OAuth flow (the client signs in as a person) replaces pasted keys; keys stay for machines.
- **Nothing is shared until a person turns it on.** Settings › Coding agents › Shared lists, grouped by where they
  come from: ConXist's tools, each MCP server's and integration's tools *that the workspace already allows*, published
  agents, and published skills. Each has a switch, off by default (table `mcp_shares`: workspace, kind, reference).
  A tool that changes things can be shared only after a warning that names the service and says the switch is the
  approval for every call (the owner's write-tool rule of 2026-11-27, applied here too). A tool the workspace stops
  allowing stops being shared.
- **What a coding agent gets.**
  - *Tools*: each shared tool, named as agents see it (`github__search_code`, `get_transcript`). Calls go through
    `McpToolbox` exactly as test runs do: allowed checked again on every call, the same size and time caps, the
    workspace's own credential. Results go back as tool results; the coding agent's model reads them as data.
  - *Agents as tools*: each shared published agent is a tool `ask_<agent>` with one input (`request`, up to 20,000
    characters) that runs the agent's **live** version once, on the workspace's provider key, with its tools,
    sources and limits, and returns its answer. Drafts are never run from outside.
  - *Skills as prompts*: each shared published skill is an MCP prompt named by its slug that returns the live
    version's SKILL.md body, with variables filled in. Claude Code shows prompts as slash commands
    (`/mcp__conxist__bug-report`).
- **Changes take effect at once.** Publishing an agent or skill, restoring a version, archiving or deleting one,
  switching a share, unticking an allowed tool, removing a server or turning off a key each raise a Modulith event.
  The `mcp` module listens and updates that workspace's server, which sends `notifications/tools/list_changed` or
  `notifications/prompts/list_changed` to its open sessions. Nothing restarts. A call always reads the current state,
  so even a client that ignores the notification can't call something no longer shared. ConXist's own agents already
  read their definition on every run; this record adds nothing there.
- **Guards.** The endpoint checks the `Origin` header (MCP's rule against DNS rebinding for local servers) and
  refuses a request with no valid key. Each key may make 60 calls a minute and run one agent at a time. Every call is
  logged with tool, key name, workspace, time taken and outcome, never arguments or results; the Access keys tab shows
  last use and calls today.

## Consequences

- A coding agent can read meetings, code, issues and email the workspace allows, ask a published agent, and pull a
  team skill, with one line of setup, and sees changes without reconnecting.
- A shared tool runs on the workspace's sign-in, not the caller's. Anyone holding a key gets that reach; the screens
  say so and the key is shown once.
- The product page's "MCP access" line moves from coming soon to available once this ships (not before).
- Codex reads tool list changes when it next lists tools; whether it acts on `list_changed` mid-session is to be
  checked during the build.
- REFACTOR LATER: OAuth sign-in for people; per-key shares if the owner wants one key to see less than another;
  a run's cost shown to the caller; when ConXist is hosted, keys bound to a person and per-person rate limits.
