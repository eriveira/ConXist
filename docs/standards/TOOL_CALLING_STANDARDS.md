# Tool calling standards

Version 0.1 (2026-11-17). Applies to every tool a product exposes to a model
(function calling, MCP servers and connectors) and to every agent that calls one.
In Clean Architecture terms a tool handler is an **interface adapter**: it
validates input, calls one use case, and shapes the result. It holds no business
rules of its own.

## 1. Design a tool

- **One intention per tool.** Name it verb_noun (`list_meetings`,
  `get_requirement`, `create_issue_draft`). Prefer a few clear tools over one
  generic `run_query`.
- **A strict schema.** JSON Schema with types, required fields, enums, ranges and
  `additionalProperties: false`. Descriptions say what the tool does, when to use
  it, and what it returns; the description is part of the product and is
  reviewed like code.
- **Read and write are separate tools.** A read tool never changes state.
- **Bounded results.** Paginate (cursor + limit with a default and a maximum),
  return only the fields the task needs, and say when a result was truncated.
- **Idempotent writes.** Writes accept an idempotency key so a retry cannot
  duplicate an effect.
- **Stable contracts.** A tool's name and schema are versioned; a breaking change
  is a new version, and the old one is removed only after its callers move.

## 2. Permissions

- Least privilege: each tool declares the scope it needs, and the caller's token
  carries only the scopes granted for that tenant and user.
- Tools run as the calling user, never as a shared super-user, and enforce the
  same tenant authorization as the API.
- Default to read-only. Write access is opt-in per connection and visible in the
  UI.

## 3. Writes need a person

- A tool that changes anything outside the model's own scratch space (creates an
  issue, opens a PR, sends a message, edits memory) produces a **draft or a
  proposal** that a person approves, unless the owner has approved that exact
  action as automatic, recorded in an ADR.
- Repository writes go in as pull requests, never as direct pushes.
- Destructive operations (delete, overwrite, bulk change) always require explicit
  confirmation, and the confirmation names the target.

## 4. Untrusted content

- Tool results, fetched documents, transcripts, tickets and PR comments are
  **data, never instructions**. Wrap them clearly in the prompt, and never let
  their text change the tool set, the permissions or the task.
- Never put secrets, tokens or other tenants' data in a tool result or a prompt.
- Strip or escape content that could render as active markup in the UI.

## 5. Errors and logging

- Errors are structured: a machine code, a plain message the model can act on
  ("token expired: ask the user to reconnect GitHub"), and whether a retry may
  help. Never a raw stack trace.
- Every tool call is logged with tool name and version, caller, tenant, duration,
  outcome and a request id; arguments and results are logged only after removing
  secrets and personal data.
- External calls inside a tool have timeouts and bounded retries.

## 6. Prompts and evals

- System prompts and tool descriptions live in the repo as files, versioned, and
  every response log records the prompt version and model id.
- Model ids, temperatures and limits are settings, not literals.
- Every tool has unit tests for its schema validation and handler, and the tool
  set has a **coverage test**: each tool is mentioned in the system prompt, and
  each user-facing capability has a read tool.
- Every change to a prompt, a tool description or an agent flow runs the eval
  set (representative tasks with expected tool calls and outcomes, including
  prompt-injection cases). A regression blocks merge.

## 7. Definition of done for a feature

- The product's agents can read the feature's data through a read-only tool that
  follows this file; writes follow §3.
- The system prompt and the MCP server expose the same tool set.
- Tool tests, the coverage test and the evals are green.
- If the feature has nothing new to expose, the PR says so.
