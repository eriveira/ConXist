---
name: build-flow
description: Use for any feature, fix or change in a WDTeams project repo (ConXist, C2Trade or any other), UI or not: design first, build to Clean Architecture, engineering, security and UX passes, product page and tool calling, UAT, PR.
---

# Build flow

The rules live in the project's `docs/standards/` (ENGINEERING_STANDARDS.md,
UI_STANDARDS.md, TOOL_CALLING_STANDARDS.md) and its `AGENTS.md`. Read `AGENTS.md`
first. This skill is only the order to apply them in; if they ever differ, the
standards and `AGENTS.md` win.

## 1. Start clean
- The default branch (`main` unless `AGENTS.md` names another) up to date, nothing uncommitted. Report any open branch or PR instead of
  stacking on it. Branch as `feat/…`, `fix/…`, `chore/…`.

## 2. Design, then wait for the go
- Propose briefly: what changes, which modules and classes, the layer of each
  (entities, use cases, adapters, frameworks), any table or column with keys, the
  tools the product's agents get, and how it will be tested.
- UI work (anything a person sees): run `ux-preflight` and show its UX read; run
  `ux-flows` when the screen is a common type; bring references and a mock for a
  new screen (UI_STANDARDS.md §1).
- Say what the product page will gain.
- Do not build until the owner says go. Build in the owner's slices; extra ideas
  are suggestions, not code.

## 3. Build, test first
- For a bug: a failing test first. For a feature: use-case tests first, then the
  adapters.
- Dependencies point inward; frameworks stay at the edge; no hardcoded values;
  colors and fonts only as tokens.

## 4. Question it once it works
- Is this the simplest design that will take the next feature? Has it been solved
  before, here or in a well-known system? Is the framework used fully? Fix it.

## 5. Reviews
- Run the `engineering-reviewer` agent on the diff (or follow its checklist).
  Repeat until no BLOCKER or FIX NOW is left.
- Touching auth, data, secrets or tools: run `security-reviewer`.
- UI: screenshots light and dark at the widths in UI_STANDARDS.md, run
  `ux-audit`, apply with `ux-fix`, then run `ux-reviewer`.

## 6. Definition of done
- **Product page** describes the change as a user or buyer sees it, or the PR
  says why nothing changed.
- **Tool calling**: a read tool exposes the new data, the system prompt mentions
  it, tool tests and evals green; or the PR says why there is nothing to expose.
- README, AGENTS.md or an ADR updated where they went stale.

## 7. UAT, then git
- Local UAT passes. Then commit (Conventional Commits), push the branch and open
  the PR with the template filled in.
- Never merge, deploy or apply migrations without the owner's explicit go.

## 8. Grow the rules
- When the owner states a new convention, propose the edit (to `AGENTS.md`, or to
  the shared standards if it fits every project) in the same PR.
