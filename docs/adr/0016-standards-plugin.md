# 0016. Load build-flow from the shared standards plugin

- Status: Proposed
- Date: 2026-11-25
- Decided by: Eugenio Riviera ("modify build flow to use ponytail ... remove it from conxist repo and have it
  installed for all projects", and "sounds good" on the plan, project thread, 2026-11-25)
- Changes: ADR 0001 (build-flow is no longer copied)

## Context

ADR 0001 copies the shared standards into the repo. The copied build-flow drifted
from the one saved on the owner's claude.ai account, and the owner wants one
build-flow for every project. The shared standards now live in their own private
repo, `eriveira/engineering-standards`, which is also a Claude Code plugin.

## Decision

- build-flow is no longer copied. `.claude/settings.json` turns on the
  `engineering-standards@wdteams` plugin and `ponytail@ponytail`, so local, cloud and teammate sessions load
  it from that repo. The owner also has it installed at user scope for every repo.
- Still copied, as in ADR 0001: `docs/standards/` (people and CI read it), the
  reviewer agents (the CI reviewer reads `.claude/agents/`), and ConXist's own UI hook.
- build-flow 0.2.0 uses the `ponytail` skill for the smallest complete change and
  `ponytail-review`, and keeps the useful superpowers rules (debugging, review
  feedback, evidence before claims). The superpowers plugin is turned off for this
  repo, since build-flow now covers what we used from it.

## Consequences

- One build-flow to change: edit the standards repo and bump its version.
- Agents without Claude Code plugins (Codex) read `skills/build-flow/SKILL.md` in the
  standards repo, as `AGENTS.md` says.
- In Claude Code the plugin also adds its own reviewer agents and a generic UI hook
  next to ConXist's copies; they say the same thing.
