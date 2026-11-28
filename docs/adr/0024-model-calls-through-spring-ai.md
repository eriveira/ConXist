# 0024. Every model call goes through Spring AI

- Status: Proposed
- Date: 2026-11-28
- Deciders: owner, Claude
- Changes: ADR 0002 (the stack adds Spring AI for model calls, not only for the MCP server), ADR 0012 and ADR 0014
  (Claude and OpenAI are called through Spring AI's chat models)

## Context

The owner wants ConXist to work with many models at once through one abstraction, Spring AI's, rather than one vendor
library per provider (2026-11-28). Agents already pick their own provider and model; what was missing is the common
layer that lets a new provider (Gemini, Mistral, Bedrock, a local model) plug in without a hand-written adapter. A spike
(`App/specs/spring-ai-spike/`) checked Spring AI 2.1.0 on Spring Boot 4.1.2: one key per workspace, effort and thinking,
and a tool call that ConXist decides on all work.

## Decision

- **Model calls use Spring AI 2.1.0** (`spring-ai-bom`, `spring-ai-anthropic`, `spring-ai-openai`; no starters, so no
  key is configured globally). `ClaudeModel` and `OpenAiModel` keep the `ModelPort` they implement, so the run, its
  limits, Settings and Try it do not change; each now calls `AnthropicChatModel` or `OpenAiChatModel`.
- **ConXist's run loop stays ConXist's.** A Spring AI chat model makes one call per turn and runs no tool. The tools
  ConXist hands it (`SpringAiTurns`) only describe each tool and refuse to be run, so every call still goes through
  `RunTools` with its checks, and later through the guardrail gate.
- **Neither a key nor an address comes from the environment.** Spring AI falls back to `ANTHROPIC_API_KEY`,
  `ANTHROPIC_AUTH_TOKEN`, `ANTHROPIC_BASE_URL`, `OPENAI_API_KEY` and `OPENAI_BASE_URL` for any client it builds itself.
  ConXist builds both clients a chat model uses (the second is the first's asynchronous view, sharing its connections),
  with the workspace's key, no retries and an explicit address (the configured one, else the provider's own), and the
  chat model's defaults say the same. Tests check each key's chat model carries that key and address.
- **Spring AI logs at WARN.** At DEBUG it logs a whole prompt, meetings and tool results included, so
  `logging.level.org.springframework.ai` stays at `WARN`.
- **Checking a key and listing its models stay on each provider's own library**, which Spring AI wraps but does not
  expose for this. Spring AI ships older versions of those libraries, so `anthropic-java-core` and `openai-java-core`
  are pinned to ConXist's.
- **Lost, accepted by the owner (2026-11-28):** Claude no longer gets strict tool inputs or Anthropic's server-side
  fallback model (Spring AI calls the plain Messages API); `fallbacks` leaves `conxist.runs.models`. ConXist checks the
  input of its own tools and of each integration's before they run; an MCP server's tool gets the input with only a
  length check, and checks it itself, as before. OpenAI keeps strict tools when every tool of the call is strict.
- **What Spring AI drops, ConXist makes up for:** Claude is sent only a schema's `properties` and `required`, so an MCP
  server's pointers into its own `$defs` are replaced by what they point to and only text names stay in `required`
  (`ToolSchemas`). An MCP schema is untrusted: a pointer back to itself becomes an open schema, and the result stops
  growing at 2,000 parts and 64 levels. Any failure inside Spring AI ends the turn as a failure that names only its kind.
- **An answer with no content.** Spring AI hands back a Claude answer with no content as no answer and drops its stop
  reason, so it is read as an empty answer; a refusal that says why is still a refusal. An OpenAI answer without a
  choice is a failure, as before.

## Consequences

- A new provider is a new `ModelPort` adapter over that provider's Spring AI chat model, its key check and its model
  list; the run code does not change.
- OpenAI now gets the run's rules as the system message instead of the developer message; OpenAI treats them the same.
  A failed tool's result reaches Claude without the error flag; its text already says it failed.
- Nothing visible changes in the app, and nothing is deployed.
- Claude no longer sees `additionalProperties: false`, so a tool may get keys its schema does not list; ConXist's own
  read tools (`list_sources`, `list_meetings`, `get_transcript`) read only the keys they know.
- REFACTOR LATER: an MCP schema's other top-level keywords (`anyOf`, `additionalProperties`) are not sent to Claude;
  the two adapters' client caches could be shared once a third provider exists; a Claude answer with no content counts
  no tokens; check the provider libraries ConXist pins against Spring AI's on each Spring AI upgrade.
