# 0014. Workspace Settings: General, Connectors (models) and Topics

- Status: Proposed
- Date: 2026-11-24
- Deciders: owner, Claude

## Context

Before the topic classifier (the first Knowledge workflow, see the storage plan) can sort meetings, each workspace
needs its own list of topics, and the owner wants to choose which model each task runs on, per workspace, with more
vendors later. The owner approved the mock `App/mocks/workspace-settings-v1/` on 2026-11-24: a Settings button in
the workspace menu, and Settings with General, Connectors and Topics. Sources stay in their own menu; Connectors in
Settings holds models now and storage later. Invite members was dropped.

## Decision

- **General**: `PUT /api/workspaces/{id}` renames a workspace and edits its description, under the same rules as a
  new one (ADR 0007). Like the other `/api/workspaces` calls it names the workspace in the path, not the header.
- **Topics** live in the `workspaces` module: table `workspace_topics` (V13) with a name of 1 to 60 characters,
  unique per workspace ignoring case, and a description up to 300. A workspace keeps up to 50, which the app enforces.
  `/api/topics` (GET, POST, PUT and DELETE `/{id}`) acts for the workspace in `X-Workspace-Id`.
- **Models**: the API's settings list the models a workspace may pick (`conxist.runs.models`: id, label, whether the
  provider may fall back). Each preset keeps its default model, effort and token limit there. A workspace picks only
  the model for a preset; table `workspace_model_picks` (V14, `runs` module) holds the picks, and a preset with no
  pick, or a pick no longer listed, runs on its default. `GET /api/model-settings` returns the vendor (connected when
  the key is set), the presets and the models; `PUT /api/model-settings/presets/{preset}` saves a pick. Test runs use
  the pick. The key stays in `CONXIST_ANTHROPIC_API_KEY` and never reaches the app.
- The app shows "used by" per preset from the agents list, which now carries each agent's model setting.
- Coming soon, shown but not built: more vendors, keys saved in Settings (they need sign-in), the zero data
  retention rule, a monthly limit, and Storage (where Knowledge and Memories will be saved).

## Consequences

- Changing a pick changes every agent on that preset in the workspace at once, which is the point: agents name a
  setting, never a model (ADR 0010).
- Removing a model from the list does not break a workspace that picked it; it falls back to the preset's default.
- Removing a topic changes nothing else yet. Once Knowledge files exist per topic, removing or renaming one must ask
  where its files go (in the mock's note).
- No read tool exposes topics yet. The classifier (next step) reads them from its skill first; a `list_topics` tool
  follows if the agent needs the live list.
