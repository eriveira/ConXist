# 0015. Start fresh: no agent templates and no built-in skills

- Status: Proposed
- Date: 2026-11-25
- Decided by: Eugenio Riviera ("remove all agents, templates and skills from ConXist, let's start fresh", and "go"
  on the plan, project thread, 2026-11-25)
- Changes: ADR 0010 (templates), ADR 0011 (built-in skills), ADR 0014 (template tiers)

## Context

ConXist offered four agent templates (Meeting summarizer, Requirements analyst, Decision logger, Topic classifier)
plus Blank, and three built-in skills (Requirements analysis, Meeting summary, Plain writing). They were written by
ConXist in English and Spanish. The owner wants a clean slate: every agent and skill in a workspace is one the team
wrote or imported.

## Decision

- New agent asks only for a name. The agent starts as an empty draft (no purpose, instructions or tools; accepts a
  transcript, gives back free text, precise style, default limits) on the workspace's default model. It can't be
  published until it has instructions, as before.
- No built-in skills. The Skills page lists only the workspace's own skills, written in ConXist or imported from a
  SKILL.md; its filters are All, Yours and Imported. "Copy a built-in skill" and `POST /api/skills/{id}/copy` are gone.
  An empty Skills page invites the first skill.
- `POST /api/agents` takes `{ "name": … }` only. The `template` and `language` fields are gone.
- Without templates there are no tiers. `conxist.runs.fast`, `balanced` and `thorough` become one setting,
  `conxist.runs.new-agents` (model and effort, Sonnet at medium), where a workspace's new agents start until it picks
  its own default in Settings (ADR 0014).
- The app stops sending `Accept-Language`: only built-in skills and template briefs were written by ConXist in the
  person's language, so the API no longer reads it. The app's own text is still English or Spanish.
- No migration. Nothing stored names a template, and skills marked built-in were never stored. An agent draft that
  pinned a built-in skill would now show that skill as missing; no such agent exists outside test data. Comments in
  V12 and V15 still mention built-in skills and the balanced setting; they stay as written so Flyway checksums hold.

## Consequences

- The product page no longer says agents start from a template or that skills can start from a built-in one.
- Ideas for starters can come back later as importable SKILL.md files or examples in the docs, without code.
