# 0001. Use the shared WDTeams engineering standards

- Status: Accepted
- Date: 2026-11-17
- Decided by: Eugenio Riviera

## Context

The owner wants the same best practices in every project, not only ConXist:
code inspired by Clean Code and Clean Architecture, UI built with the installed
UX skills, a product page kept current with every change, and rules for tool
calling.

## Decision

ConXist copies the shared standards (`docs/standards/`, `.claude/agents/`,
`.claude/skills/build-flow/`, `.claude/hooks/`, `.github/` templates) from the
shared engineering-standards layer, version recorded in
`docs/standards/VERSION`. Only `AGENTS.md`, `brand/` and `apps/landing/` are
ConXist's own.

## Consequences

- One place to improve the rules; each project picks up a change by re-syncing.
- A project-only rule goes in `AGENTS.md`; loosening a shared rule needs an ADR.
- Until the shared layer has its own repository, its source is the project
  shared folder `Engineering/standards/`.
