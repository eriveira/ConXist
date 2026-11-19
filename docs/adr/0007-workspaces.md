# 0007. Several workspaces, named in each request

- Status: **Proposed** (needs the owner's go before migration V8 is applied anywhere shared)
- Date: 2026-11-19
- Decided by: Eugenio Riviera (workspace setup mock approved, "build it", project thread, 2026-11-19)

## Context

Until now the API acted for one configured workspace (`conxist.workspace.default-id`). The owner asked that a
first-time person create their workspace (name and an optional description of up to 500 characters) and that a
person can keep several workspaces, switching between them from the header. There is still no sign-in (ADR 0002).

## Decision

- A `workspaces` module owns a `workspaces` table: id, name (1 to 80 characters, unique ignoring case),
  description (up to 500 characters, counted as characters a person sees), created_at.
  `GET /api/workspaces` lists them; `POST /api/workspaces` creates one.
- Every request that reads or changes workspace data names its workspace in the `X-Workspace-Id` header. The
  platform's `CurrentWorkspace` reads it and checks it exists through the `WorkspaceDirectory` port, which the
  workspaces module implements. No header or a malformed one is refused with `workspace.required` (422), an unknown
  one with `workspace.not-found` (404). Requests that need no workspace (listing workspaces, Plaud's callback) work
  without it.
- Work outside a request (the scheduled recorder check) is told the workspace id and runs for every workspace.
- The switcher's source counts come from `workspace_sources`, a read model fed by `SourceAdded`, with no foreign key
  into the sources module (like the hub's, ADR 0002). Writes are idempotent, so a re-delivered event counts once.
- Migration V8 gives data made before workspaces existed a row named "My workspace" (then "My workspace 2" and so
  on), so it stays reachable. A database with no sources gets no row, and the app shows the first-run page.
- The web app keeps the last workspace used in the browser and opens it next time, or the oldest one.

## Consequences

- Until sign-in exists, anyone who can reach the API on this machine can name any workspace. Workspaces separate
  work; they are not a security boundary yet. Sign-in must bind a person to their workspaces before hosting.
- Workspaces cannot yet be renamed or deleted; the app does not claim they can.
- `CONXIST_WORKSPACE_ID` is gone.
