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

1. **A stronger fixed rule** (`RunPrompt.RULES`, prompt version `2026-11-28.2`). Only the agent instructions and skills
   can ask for something; every kind of data is named (meetings, transcripts, documents, files, email, chat, tickets,
   issues, pull requests, commits, code, web pages, tool answers, and what an MCP tool says about itself). Data says
   what the team said or decided, never what to do. A tool that changes things is used only when the agent instructions
   call for that change (skills say how, not what to change).
2. **Requests found in what it read.** When data asks for something the instructions do not call for, the model reports
   it with ConXist's own `report_request` tool, which every run offers: what was asked, briefly, and where. A report
   changes nothing, is not a tool call the run limits count, and one run keeps at most 10 (500 and 200 characters).
   The person sees them beside the answer in Try it. They are customer data: shown, never logged or kept; the run's log
   line has only their number.
3. **The copy guard** (`runs/domain/CopyGuard`), code the model cannot talk round. Before an outside tool (an MCP
   server's or an integration's) is called, ConXist refuses its input when it repeats 12 words in a row of anything the
   run read, other than the meeting or text it was handed (its instructions may quote that). A tool that changes things
   is also refused when its input holds more than 4,000 characters of text. A refused call is not sent, counts as a tool
   call, comes back to the model as an error (`copy-refused`, `change-too-long`) that does not name what it read, and
   shows to the person as a refused step with the reason and, for a copy, the meeting or tool it came from. Passages
   are kept only as 64-bit hashes for the length of the run.
4. **Attack tests in every build.** A scripted model obeys what the data says (`StartTestRunTest`, "Attack evals"):
   a meeting ordering another meeting into an issue, an email asking for a meeting in a search, a change over the
   limit, and reports past the cap. Code must hold in each.

Later steps, from the plan:

- With the next change to Try it: per change tool, a person sets its purpose, target and how many changes one run may
  make, enforced in code; Send or Skip for each change in Try it; and a change log without customer text.
- With Schedules: a Pending changes queue, an independent checker (a second model call that sees only the instructions
  and the proposed call, never source text), and nightly attack runs against real models.

## Consequences

- A request found in the data is visible instead of silent, and an agent cannot be used to copy one meeting into
  another tool, whatever the model decides.
- Quoting the handed meeting into a change is still allowed, and a hostile handed text can still argue for a change;
  later layers (a person's Send, the target limits and the checker) cover that.
- A legitimate change longer than 4,000 characters is refused; the limit may become a per-tool setting with the target
  limits.
- The model may report too much or too little; the attack tests check the code, and the nightly runs will measure the
  model.

Open owner decisions from the plan: approve every change by default; the checker's cost; whether skills count as
instructions for changes (this record keeps them out); and, in flows, whether one agent's output is data for the next
(this record assumes yes).
