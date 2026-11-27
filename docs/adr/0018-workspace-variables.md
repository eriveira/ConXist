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
  lacks stays as written, and the agent page lists such names with a link to Settings.
- **Topics move, then go**: V19 gives each workspace that had topics one variable, `topics`, listing them by name
  with each description in brackets, and drops `workspace_topics`. Nothing read topics yet.

## Consequences

- Deleting or renaming a variable is not blocked by the agents that use it: their runs read `{{name}}` as written,
  and the agent page says so. A check of uses, like skills' (ADR 0013), can follow if teams need it.
- The skill page does not list unknown names yet; the agent page does, for its own text.
- A value changes every agent at its next run, published versions included, since the value is read at run time.
