# 0023. A simpler agent page: no Style, shapes or Knowledge; run limits per workspace

- Status: Proposed
- Date: 2026-11-28
- Deciders: owner, Claude
- Changes: ADR 0010 (an agent loses style, what it takes in and gives back, its Knowledge sources and its own limits),
  ADR 0012 (the Test panel becomes "Try it", answers are plain text, the run limits are the workspace's)

## Context

The owner's meeting on 2026-11-27 ("Rediseño agentes") found the agent page too long and some of it unclear: "Creative"
style reads as an invitation to make things up, "Takes in / Gives back" offers fixed kinds where teams need their own,
Knowledge ties an agent to a list of sources, Effort takes three large cards, MCP tool groups don't fold the way the
model lists in Settings do, and Limits and Test don't say what they are for. The mock `App/mocks/agents-v2/` showed the
answer and the owner chose to build it in slices; this is slice 1 ("Yes, build slice 1", 2026-11-28). Tools on skills,
the ConXist Builder and Download come in later slices.

## Decision

- **Style is gone.** Every agent sticks to what it reads. The run prompt's last lines are now "Stay close to what you
  read; do not add ideas of your own. Give back your answer in plain text." (`RunPrompt.VERSION` `2026-11-28.1`).
- **Takes in / Gives back and answer shapes are gone,** with the JSON schema sent to the model (`ModelPort.Request`
  has no output schema) and the shape check on test runs. They come back with Flows, with kinds a team can add to.
- **Knowledge is gone from the agent.** ConXist's own tools (`list_sources`, `list_meetings`, `get_transcript`) reach
  every source of the agent's workspace and no other; a source id of another workspace is refused as
  `source-not-found`, the same as one that does not exist. What goes into the workspace's knowledge gets its own page
  when Knowledge works.
- **Run limits belong to the workspace.** Steps (1 to 20), time (10 to 600 seconds) and tool calls (0 to 50), default
  6, 120 and 10. V25 adds `workspace_run_limits` (one row per workspace that changed them); `GET` and
  `PUT /api/run-limits` read and change them (`run-limits.steps`, `run-limits.time`, `run-limits.tool-calls`), in
  Settings › General. Every run in the workspace keeps them; a deleted workspace takes its row (`WorkspaceData`).
- **Stored agents keep working.** An agent definition is written as shape 2; shape 1 rows are read with the removed
  fields ignored and are rewritten as shape 2 when the draft is next saved or restored. Published versions are never rewritten; they read the
  same way.
  `PUT /api/agents/{id}/draft` ignores the removed fields if an older page sends them.
- **The page.** Five sections: What it is, Instructions, Skills, Tools, How it thinks. Each tool group (ConXist, each
  MCP server, each integration) folds, its heading saying how many tools are picked and how many of those change
  things; a group opens when it has picks. Effort is a three-stop slider that names the stop and what it is for. The
  side panel is "Try it", with a line on what it is for, and shows the answer as text.

## Consequences

- V25 needs the owner's go before it is applied. It carries no agent's own limits over: an agent that had, say, 20
  steps now runs at its workspace's limits (6, 120 and 10 until changed). Nothing is deployed, so nothing in use breaks.
- An agent can no longer be kept away from some sources of its workspace, and agents saved with no Knowledge sources
  now reach every meeting of the workspace through `get_transcript`. The workspace is the boundary; the owner accepted
  this with slice 1 (2026-11-28). With a tool that changes things ticked (ADR 0022), a hostile meeting could ask the
  model to read another meeting and send it out through that tool: code keeps every read as data and marks the tool,
  but whether the model obeys rests on the prompt rules and the person's tick (eval in `StartTestRunTest`). A
  deterministic guard would also block the intended use (filing an issue from what a meeting said), so none is added;
  for the owner to confirm. A per-agent narrowing, if needed, comes back with Knowledge or Flows.
- Agents that relied on a shape now answer in free text; nothing consumes a shape yet.
- `agent.limits-*` and `agent.source-missing` are no longer returned. A shape 2 row with a field it lacks is refused;
  only shape 1 rows skip unknown fields.
- REFACTOR LATER: the bounds live in `RunLimits`, V25's checks, the web's `runLimits.ts` and the error texts (`GET
  /api/run-limits` could return them); changing the limits writes no log line; `/api/run-limits` needs the same authorization as the rest of Settings once sign-in exists.
