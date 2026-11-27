# 0018. Workspace variables replace topics

- Status: Proposed
- Date: 2026-11-27
- Deciders: owner, Claude
- Changes: ADR 0014's Topics section (Settings › Topics and table `workspace_topics`)

## Context

ADR 0014 gave each workspace a list of topics for a topic classifier that was never built. In the connectors demo
(`App/mocks/connectors-v5/`) the owner turned Topics into **Variables**: free-form names and values a workspace's
agents and skills share, such as `company_name`, a product's URL or the list of topics to sort meetings into. The
owner approved v5 on 2026-11-27, which proposed a value up to 2,000 characters, 100 per workspace, names unique
ignoring case, and use as `{{name}}` in instructions.

## Decision

- **Settings › Variables** replaces Topics, in the `workspaces` module: table `workspace_variables` (V19) with a name
  of 1 to 60 characters and a value of 1 to 2,000, unique per workspace ignoring case; up to 100, kept by the app
  under a lock on the workspace. `/api/variables` (GET, POST, PUT and DELETE `/{id}`) acts for the workspace in
  `X-Workspace-Id`. Add and Edit open a form in the list itself, as the mock shows; Delete asks first.
- **Names are made for braces**: letters, digits and underscores, starting with a letter, so `{{name}}` reads the
  same in every instruction and nothing else in a text looks like one.
- **Runs fill them in**: the `platform` port `WorkspaceVariables` (answered by `workspaces`) gives a run the
  workspace's values; `RunPrompt` puts each value in place of `{{name}}` (any case, spaces allowed inside the braces)
  in the agent's purpose and instructions and in its skills, before any text is marked. A value is written by a
  person in the workspace, like the instructions themselves, so it is not treated as data. A name the workspace
  lacks stays as written, and the agent page lists such names with a link to Settings. Filling is one pass (a
  value holding braces is not filled again), and a block that grows by more than 20,000 characters is refused
  (`run.variables-too-long`), so a skill can't make every run huge. The prompt version is 2026-11-27.2.
- **Not for secrets, stored as plain text**: a value is text every agent and skill of the workspace reads as written,
  imported skills included, and an agent may pass what it reads to a tool. The value field says it is not for
  passwords or keys, so values are not sealed like keys and tokens (ADR 0004). If teams need secrets in instructions,
  that is a separate, sealed kind with its own rules.
- **Topics move**: V19 gives each workspace that had topics one variable, `topics`, listing them by name with each
  description in brackets (cut at 2,000 characters), and renames `workspace_topics` to `workspace_topics_v13`
  instead of dropping it, so nothing is lost; a later migration drops it. Nothing read topics yet.

## Consequences

- Deleting or renaming a variable is not blocked by the agents that use it: their runs read `{{name}}` as written,
  and the agent page says so. A check of uses, like skills' (ADR 0013), can follow if teams need it.
- The skill page does not list the names a skill uses yet, imported skills included; the agent page lists unknown
  names in its own text. Showing a skill's names before it is published or pinned is the next step.
- A value changes every agent at its next run, published versions included, since the value is read at run time.
