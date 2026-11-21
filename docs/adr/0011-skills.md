# 0011. Skills: written once, versioned, pinned by agents

- Status: **Proposed** (needs the owner's go before migration V12 is applied anywhere shared)
- Date: 2026-11-21
- Decided by: Eugenio Riviera (agents plan and mock approved; "build a real flow step by step", Skills under Agents
  accepted on the decision card, project thread, 2026-11-20)

## Context

Step 2 of the owner's four steps (Agents, Skills, Test run, Schedules). A skill is a way of working a team writes once,
such as a ticket format or a writing style, and any agent follows. The mock (`App/mocks/agents-v1/`, Skills tab and
skill editor) shows a list with built-in, team and imported skills, a skill page with versions, and an agent's Skills
section where a skill stays at the version it was added at.

## Decision

- Skills live in the `agents` module, next to agents, because an agent pins skill versions and both share the same
  tools and lifecycle. No new module and no new cross-module API.
- A skill's definition follows the SKILL.md format so a team's existing skills import as they are: name (1 to 64
  characters, unique per workspace ignoring case, and never the name of a built-in skill), "When to use it" (up to
  1,024 characters, the SKILL.md `description`), instructions (Markdown, up to 20,000), and the tools it needs (from
  the agent tool list).
- Lifecycle as agents (ADR 0010): a draft saved as the person types with a revision check (`skill.changed`, 409),
  Publish to numbered versions that never change, with an optional note (up to 140 characters), Restore into the
  draft, Archive and bring back. Publishing needs a name, "When to use it" and instructions.
- Built-in skills (Requirements analysis, Meeting summary, Plain writing) live in the code, in English and Spanish,
  at version 1 with fixed ids (`UUID.nameUUIDFromBytes("conxist.skill." + key)`). They cannot be changed; Copy makes a
  team skill from one, in the person's language. The language comes from the request's `Accept-Language`, which the
  app sets from the person's language. A later version is added in code and earlier ones are kept.
- Import reads one SKILL.md (up to 64 KB): `name` and `description` from the front matter (a slug such as
  `release-notes` becomes "Release notes"; without front matter the first `# heading` names it), the body as the
  instructions, and the tools the body names (`get_transcript` or `conxist.get_transcript`) as what it needs. Other
  front matter, including `allowed-tools`, is ignored: a skill never grants tools. It arrives as a draft for the person
  to check.
- An agent's definition gains `skills`: up to 10 pins `{skillId, version}`, one per skill. Adding a skill in the app
  pins its newest version and turns on the read-only tools it needs. A newer version never changes the agent: the
  agent shows "vN available" and a person updates the pin, then publishes the agent.
- Publishing an agent is refused with `agent.skill-missing` while a pin is not a skill version this workspace can use
  (another workspace's skill, a version that does not exist), and with `agent.skill-needs-tool` while a pinned version
  needs a tool the agent does not have. The agent view lists the pins it could resolve, scoped to the workspace.
- API: `GET/POST /api/skills`, `POST /api/skills/import`, `POST /api/skills/{id}/copy`, `GET /api/skills/{id}`,
  `PUT /api/skills/{id}/draft`, `POST /api/skills/{id}/publish`, `GET /api/skills/{id}/versions/{n}`,
  `POST /api/skills/{id}/versions/{n}/restore`, `POST /api/skills/{id}/archive` and `/unarchive`.
- The app: Agents in the header has two tabs, Agents and Skills. Skills lists built-in skills first with filters
  (All, Built-in, Yours, Imported), each with its version, the tools it needs and how many agents use it; Import and
  New. A skill's page has About, Instructions (with the tools it uses, offering to add tools its text names) and
  Reference files (coming soon), and a side panel with Used by and the SKILL.md to export. An agent's page gains a
  Skills section and its Tools say which skills need each tool.

## Changes from the mock

- Reference files and importing from a GitHub repo show as coming soon. Files are useful only once agents run
  (step 3), and storing them needs a decision on size and encryption.
- A skill folder (zip) is not imported, only one SKILL.md.
- The tool picker offers the three agent tools, not tools from GitHub or a team's own MCP server.
- "New skill" from an agent's Add menu opens a new skill; it is not added to the agent on publish. The person adds it
  from the agent after publishing.
- The publish dialog does not list what changed field by field; it names the agents that stay on their version.
- Turning off a tool a skill needs is not confirmed in a dialog. The tool shows "Off, but needed by …" and publishing is
  blocked until it is on again or the skill is removed, which is reversible and says why.
- New asks for a name in a small dialog first, the same as a new agent, so the skill is saved from the first moment.
- The Agents | Skills tabs show no counts; each page shows its own list.
- Import uses a file picker, not a drop zone.

## Data

Migration `V12__skills.sql` adds `skills` (source YOURS or IMPORTED, draft as `jsonb`, `revision`,
`published_version`, `draft_published`, unique `(workspace_id, lower(name))`) and `skill_versions` (primary key
`(skill_id, number)`, `note`). Pins are stored inside the agent definition JSON (shape `v: 1`, the new `skills` field
reads as empty in rows saved before it), so there is no foreign key from agents to skills. V12 needs the owner's go
before it is applied to any shared database.

## Consequences

- Skills can be written, imported, versioned and pinned now, but nothing follows them until agents run (step 3).
- Skill text is configuration a team writes or imports, not captured customer content, so it is not sealed like
  transcripts (ADR 0004); logs name ids only. Imported text is shown to the person before any agent uses it.
- Characters that do not show on screen (zero-width, bidirectional controls, Unicode tag characters) are removed from
  a skill's name, "When to use it" and instructions when it is imported or saved, so what a person reviews is all a
  model will read, and a name cannot pass for a built-in one.
- The app turns on only read-only tools when a skill is added to an agent; a tool that changes something would stay
  off with the "Needs … Turn on" note, because `needs` can come from imported text.
- Rules for the runtime (step 3), from this design and the security review:
  - A run reads each pinned version again, in the run's workspace only; the stored pin is never trusted on its own.
    A pin to a missing or archived skill is refused, or the run records that it used one.
  - Skill instructions go into the system prompt inside clearly marked delimiters with the skill's name, version and
    source, below ConXist's own rules. Those rules say that meeting and document text is data, and that a skill
    cannot change the tools, permissions, workspace or task.
  - The tools given to the model are exactly the agent version's `tools`; a skill's `needs` is only ever a check.
  - A built-in skill has English and Spanish text under one version. A test run uses the language of the person who
    starts it; a scheduled run (step 4) uses the language saved with its schedule, so the same pin always reads the
    same words in that schedule.
  - The eval set gets prompt-injection cases for a hostile skill: one that asks for more tools, for another
    workspace's data, or to ignore ConXist's rules (TOOL_CALLING_STANDARDS.md §6).
  - Sign-in and owner checks come before agents run, because until then anyone on the machine can import a skill.
- No read tool for skills is added yet, for the same reason as agents (ADR 0010); `list_skills` and `get_skill` come
  with the shared tool set.
