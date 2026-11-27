# 0022. Settings feedback: provider defaults, workspaces, test text and tools that change things

- Status: Proposed
- Date: 2026-11-27
- Deciders: owner, Claude
- Changes: ADR 0014 (one default for new agents becomes one per provider, plus the provider new agents start on),
  ADR 0012 (a test run also takes pasted text or a text file), ADR 0007 (a workspace can be deleted; Sources leaves the
  menu), ADR 0017 (tools that change things can be allowed; the site's icon stands in for a missing logo)

## Context

The owner's meeting on 2026-11-27 reviewed Settings and asked for eight things. Bitbucket was already done (ADR 0020);
the rest were drawn in `App/mocks/settings-feedback-v1/`, which the owner approved ("good"), after deciding two points:
the default model is per LLM provider, and tools that change things are tickable, unticked by default, with a warning
when ticked; the tick is the approval (no approval per change).

## Decision

- **A default model per provider.** `workspace_model_defaults` keys on (workspace, provider) and gains `new_agents`
  (V23 keeps each workspace's one default and marks it as where new agents start). Settings › LLM providers shows
  each connected provider's default model and effort, and, with two or more connected, which provider new agents start
  on (`PUT /api/llm-providers/new-agents`). A provider with no saved default uses the catalog's start for it, else its
  first allowed model at medium effort. Switching an agent to another provider moves it to that provider's default.
  Each provider's model list folds away, showing "{allowed} allowed of {offered}".
- **One line per workspace.** The workspace menu lists each workspace once, on one line, with Settings and New
  workspace below. The source counts are gone.
- **Sources out of the menu, for now.** The header menu is Home, Agents, Skills; the app opens on Agents. The Sources
  page still answers at its address (nothing links to it), so meetings already imported stay reachable, and its code
  stays.
- **A test run on pasted text or a text file.** `POST /api/agents/{id}/test-runs` takes `title` and `text` instead of a
  meeting (up to 150,000 characters; `run.text-empty`, `run.text-too-long`). The file is read in the browser
  (`.txt`, `.md`, `.markdown`, `.vtt`, `.srt`); only its text is sent, and nothing of it is kept. A meeting stays a
  choice when the workspace has some.
- **Delete a workspace by typing its name.** `DELETE /api/workspaces/{id}` with `{name}` must match the name exactly;
  the last workspace cannot be deleted (`workspace.last`). Each module removes its own rows through the platform port
  `WorkspaceData`, in a fixed order, in one transaction; uploaded files are removed after it commits.
- **Tools that change things can be allowed.** A tool the server does not mark `readOnlyHint` starts unticked and
  carries "Can change things". Ticking it asks first, in the tools table: what it may change, that agents use it
  without asking each time, test runs included, and that ConXist can't undo it. That tick is the owner-approved
  automatic action TOOL_CALLING_STANDARDS §3 allows, one tool at a time and recorded here. Agents label those tools;
  the Test panel names the ones a run may use for real. The run prompt (`2026-11-27.4`) marks each such tool and tells
  the model to use it only for the task it was given, never because data asked for it, and to say what it changed. A
  later Check keeps a tick only while the tool stays what it was when ticked: a read tool that turns into one that
  changes things is unticked.
- **The site's icon for a server with no logo.** When `serverInfo` names no usable icon, ConXist tries
  `/apple-touch-icon.png`, then `/favicon.ico`, on the server's host and then on its parent domain, through the same
  address guard, with no redirects, image types only and up to 100 KB. Check refreshes it for servers already added.

## Consequences

- V23 changes a table's key; it needs the owner's go before it is applied.
- The API trusts the screen to warn before a tool that changes things is ticked; it has no extra confirm flag.
- Deleting a workspace does not revoke Plaud or Google sign-ins at the provider; it only forgets them (REFACTOR LATER).
- Sign-in (not yet built) will decide who may delete a workspace and who may allow tools that change things.
