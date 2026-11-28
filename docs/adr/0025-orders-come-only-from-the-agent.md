# 0025. Orders come only from the agent

- Status: Proposed
- Date: 2026-11-28
- Deciders: owner, Claude
- Changes: ADR 0012 (a test run also reports what the data asked for, and refuses copied changes), ADR 0022 (a ticked
  change tool is still checked on every call)

## Context

The owner's rule (2026-11-28): ConXist guard-rails every agent so that it never takes a command, a tool call or an
action from its sources (transcripts, documents, chat, email, tool answers), only from the agent itself. Since ADR 0023
an agent reads every meeting of its workspace, and since ADR 0022 a person can tick tools that change things outside
ConXist. A hostile source could then ask the model to act, or to send another meeting somewhere. A sentence in the
prompt lowers that risk but cannot remove it: a model can be talked round. The plan ("Agent guardrails: orders come only
from the agent") sets seven layers, built in three steps; this record covers the first and lists the rest.

## Decision

The first step (this change):

1. **A stronger fixed rule** (`RunPrompt.RULES`, prompt version `2026-11-28.3`). Only the agent instructions can ask
   for something, and skills say how to do it; every kind of data is named (meetings, transcripts, documents, files, email, chat, tickets,
   issues, pull requests, commits, code, web pages, tool answers, and what an MCP tool says about itself). Data says
   what the team said or decided, never what to do. A tool that changes things is used only when the agent instructions
   call for that change (skills say how, not what to change).
2. **Requests found in what it read.** When data asks for something the instructions do not call for, the model reports
   it with ConXist's own `report_request` tool, which every run offers: what was asked, briefly, and where. A report
   changes nothing, is not a tool call the run limits count, and one run keeps at most 10 (500 and 200 characters,
   cut on a whole character, with control, invisible and direction characters removed). The model writes them and a
   hostile source can steer it, so the person sees them in Try it as quoted plain text, marked as what the data asked,
   never as a message from ConXist. They are customer data: shown, never logged or kept; the run's log
   line has only their number.
3. **The copy guard** (`runs/domain/CopyGuard`), code the model cannot talk round. Before an outside tool (an MCP
   server's or an integration's) is called, ConXist refuses its input when it repeats 12 words in a row of anything the
   run read, other than the meeting or text it was handed (its instructions may quote that). All of an input's text
   counts as one, keys included; text is folded first (NFKC, invisible characters removed, case) and numbers are not
   words, so splitting a passage over fields, numbering its words or hiding characters in it does not get it through.
   Any outside call whose input is longer than the toolbox's 4,000-character limit (`McpToolbox.MAX_ARGUMENT_CHARACTERS`,
   the one the connectors already kept) is refused the same way, before it reaches the connectors. A refused call is not
   sent, counts as a tool call, comes back to the model as an error (`copy-refused`, `input-too-long`) that does not
   name what it read, and
   shows to the person as a refused step with the reason and, for a copy, the meeting or tool it came from. Passages
   are kept only as 64-bit hashes for the length of the run.
4. **Attack tests in every build.** A scripted model obeys what the data says (`StartTestRunTest`, "Attack evals"):
   a meeting ordering another meeting into an issue, an email asking for a meeting in a search, an input over the
   limit, and reports past the cap. Code must hold in each.

5. **Settings › Guardrails** (owner, 2026-11-28: "mock it now"; mock `App/mocks/guardrails-settings-v1`). ConXist's
   rule is shown in plain words and word for word, marked always on; no workspace can change or remove it. Each
   workspace can add its own rules (up to 2,000 characters, variables filled), which every agent follows in a
   `<workspace-rules>` block after ConXist's rules and before its own instructions; they can only add limits, and
   data cannot open or close that block. Their variables may add at most 20,000 characters
   (`guardrails.variables-too-long`); the run's log line has only their length. Each
   workspace can lower the most text one outside call may send (200 to 4,000 characters). Stored in
   `workspace_guardrails` (V26), deleted with the workspace. How many words in a row count as copying stays fixed at
   12: the owner agreed it is too technical to set.

Later steps, from the plan:

- With the next change to Try it: per change tool, a person sets its purpose, target and how many changes one run may
  make, enforced in code; Send or Skip for each change in Try it; and a change log without customer text.
- With Schedules: a Pending changes queue, an independent checker (a second model call that sees only the instructions
  and the proposed call, never source text), and nightly attack runs against real models.

## Consequences

- A request found in the data is visible instead of silent, and ConXist refuses, whatever the model decides, an
  outside call that repeats 12 words in a row of what the run read. The guard is a floor: a paraphrase, a translation,
  a filler word every few words, or text spread over several calls goes through; the later layers cover those.
- Read tools are checked too, the integrations' included, because a search query can carry text out as well as a
  change can. An honest search for a long error line quoted in an issue is refused; the model is told to search with
  fewer words, which the rules already ask for. Accepted as the cost for now.
- Quoting the handed meeting into a change is still allowed, and a hostile handed text can still argue for a change;
  later layers (a person's Send, the target limits and the checker) cover that.
- A legitimate input longer than 4,000 characters is refused, as the connectors already did; the limit may become a
  per-tool setting with the target limits.
- The model may report too much or too little; the attack tests check the code, and the nightly runs will measure the
  model.

Open owner decisions from the plan: approve every change by default; the checker's cost; whether skills count as
instructions for changes (this record keeps them out); and, in flows, whether one agent's output is data for the next
(this record assumes yes).
