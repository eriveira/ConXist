# 0010. Agents: configured in plain language, drafted, published as versions

- Status: **Proposed** (needs the owner's go before migration V11 is applied anywhere shared)
- Date: 2026-11-20
- Changed by: ADR 0015 (2026-11-25): templates removed; New agent starts an empty draft.
- Decided by: Eugenio Riviera (agents plan and mock approved; "build a real flow step by step", defaults accepted on
  the decision card, project thread, 2026-11-20)

## Context

The owner asked for agents that are friendly to set up, editable at any time, and later placed into flows. The plan
(Claude Doc "Agents plan: configurable agents that go into flows") and the mock (`App/mocks/agents-v1/`) were reviewed.
The owner chose to build in steps, one pull request each: 1 Agents, 2 Skills, 3 Test run with a real model,
4 Schedules. This ADR covers step 1. The owner accepted the plan's defaults: a one-page builder, three thinking
levels (Fast, Balanced, Thorough) that a workspace maps to models, Anthropic Claude as the first provider (step 3),
flows that stay on a version until a person moves them, and Skills under Agents.

## Decision

- A new Spring Modulith module `agents` (domain, application, adapters, config). It depends only on the published
  APIs of `platform` (current workspace, transaction, errors) and `sources` (`SourceCatalog`, to check that a source
  an agent reads still exists).
- An agent's definition has the parts a person sees on its page: name (1 to 60 characters, unique per workspace,
  ignoring case), purpose (200), instructions (8,000), knowledge sources (up to 20 of the workspace's sources), tools,
  thinking level, style (Precise or Creative), what it takes in, what it gives back, and limits (steps 1 to 20, time
  10 to 600 seconds, tool calls 0 to 50).
- Tools are a closed list in the domain, all read-only for now: `conxist.list_sources`, `conxist.list_meetings`,
  `conxist.get_transcript`. A tool that changes something is added only with the runtime that makes it wait for a
  person's approval (TOOL_CALLING_STANDARDS.md §3).
- The draft is edited at any time and saved as the person types. Every change raises a revision number, and a save
  names the revision it was based on; a stale save is refused with `agent.changed` (409), so two tabs cannot
  overwrite each other. Saving an unchanged draft changes nothing.
- Publish copies the draft into `agent_versions` as the next number (v1, v2, ...). Versions are never changed. It is
  refused while the draft has problems (`instructions-required`, `accepts-required`, `source-missing`) or nothing
  changed since the newest version.
- Restore copies a version into the draft; nothing is published until the person publishes again.
- Archive takes an agent off the list in use; its versions stay and it can be brought back. Nothing is deleted. (ADR 0013 adds
  Delete, for an agent nothing uses.)
  "Archive is refused while a flow uses it" comes with flows (step 4 of the plan).
- Templates (Meeting summarizer, Requirements analyst, Decision logger, Topic classifier, Blank) fill a first draft,
  with the brief in the person's language (English or Spanish). Nothing stays tied to the template.
- Definitions are stored as `jsonb` in a shape of their own with a version field (`v: 1`), so the domain can change
  without breaking stored definitions. Agents' text is configuration a person writes, not captured customer content,
  so it is not sealed like transcripts (ADR 0004); logs name ids only.
- API (every request names its workspace, ADR 0007): `GET/POST /api/agents`, `GET /api/agents/tools`,
  `GET /api/agents/{id}`, `PUT /api/agents/{id}/draft`, `POST /api/agents/{id}/publish`,
  `GET /api/agents/{id}/versions/{n}`, `POST /api/agents/{id}/versions/{n}/restore`, `POST /api/agents/{id}/archive`
  and `/unarchive`.
- The app: Agents in the header opens the list (New, a card per agent with Live vN, "changes not published", Draft or
  Archived). An agent's page has seven numbered sections, saves the draft on its own ("Draft saved"), lists what
  stops publishing, and has Publish, History (view a version, Restore with a confirmation) and Archive. Test and
  Skills show as coming soon.

## Changes from the mock

- Knowledge offers the workspace's sources only. Knowledge categories come when Knowledge exists.
- Tools are the three read tools above, not the grouped picker with source and customer MCP tools; those come with
  the runtime (step 3) and connected sources.
- Skills and Test show as coming soon (steps 2 and 3), so the page has seven sections, not eight. "Uses" (which flows
  use an agent) comes with flows.
- Publish and Archive act at once, without the mock's confirmation: nothing is lost, because every version is kept
  and can be restored, and an archived agent can be brought back. A publish note and who published come with
  sign-in.
- "Discard changes" is the History action on the newest version when the draft has unpublished changes, not a
  separate button.
- Takes in is a row of checkboxes (an agent can take in more than one kind); Gives back is a list to pick one from.

## Data

Migration `V11__agents.sql` adds `agents` (draft as `jsonb`, `revision`, `published_version`, `draft_published`,
unique `(workspace_id, lower(name))`) and `agent_versions` (primary key `(agent_id, number)`). Like the hub and the
workspaces read model there is no foreign key into another module's tables. V11 needs the owner's go before it is
applied to any shared database.

## Consequences

- Agents can be configured and versioned now, but nothing runs them yet; the app and the product page say so.
- No read tool for agents is added yet, because the shared tool set and MCP server do not exist yet. When they do,
  `list_agents` and `get_agent` are the first agent tools (plan, Tool calling).
- Until sign-in exists (ADR 0002), anyone who can reach the API on this machine can change any workspace's agents.
- Rules for the runtime (step 3), from the security review:
  - A run is told its workspace id and looks up every knowledge source again, in that workspace only. Stored source
    ids are never trusted, because a source can be removed after publishing.
  - The tools given to the model are exactly the version's `tools`, matched against the server's own registry; never
    text from instructions, transcripts or tool results. Tool results go into the prompt marked as data.
  - Limits are enforced by the runtime, not asked for in the prompt. Step 3 adds prompt-injection cases to the evals.
  - A tool that changes something gets `readOnly = false` and cannot run without a person's approval; a test fails
    today if any tool is not read-only.
- Stored versions are read back through the same rules as new drafts, so a limit may only be loosened; tightening one
  needs a migration of the stored definitions first.
