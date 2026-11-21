# 0013. Delete agents and skills, never while something uses them

- Status: Proposed
- Date: 2026-11-21
- Deciders: owner, Claude

## Context

The owner asked for Delete on agents and skills, with a safety check: something in use can't be deleted, and the
person is told why. Until now only Archive existed (ADR 0010, ADR 0011), which keeps every version. Mock:
`App/mocks/delete-v1/`.

## Decision

- Delete removes the agent or skill and all its versions for good, in one transaction, at the revision the page read
  (a change in another tab refuses it with `*.changed`). No migration: versions go first, then the row.
- Before the confirm, the page asks `GET /api/{agents|skills}/{id}/uses`. While the list is not empty the dialog
  says what uses it, links there, offers Archive instead, and has no Delete button. `DELETE` checks again inside the
  transaction and refuses with 409 `agent.in-use` or `skill.in-use`; the dialog then shows the fresh list.
- A skill is in use when any agent of the workspace, archived ones too, follows it in its draft or its live version.
  An older version that followed it does not count: restoring that version later shows "skill missing", as for a
  skill from another workspace. Built-in skills can't be deleted (`skill.built-in`).
- Nothing uses an agent yet. The `AgentUses` port in the agents module answers for it; it is empty until flows and
  schedules (step 4) exist and give their uses. The page and the API already handle a non-empty answer.
- After deleting, the list page says "{name} was deleted."

## Consequences

- Deleting can't be undone; the confirm says so and points to Archive for keeping it.
- Test runs keep nothing, so no run history is left pointing at a deleted agent.
- When flows and schedules arrive, they must provide `AgentUses`, or an agent they use could be deleted.
