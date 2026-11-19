# 0008. Delete a meeting, and keep it out of later imports

- Status: **Proposed** (needs the owner's go before migration V9 is applied anywhere shared)
- Date: 2026-11-19
- Decided by: Eugenio Riviera (meetings mock approved; build order "view and history, then delete, then edit";
  "go" for delete, project thread, 2026-11-19)
- Extends: ADR 0006 (the source page)

## Context

The owner asked that a person can delete an imported meeting. The mock
(https://claude.ai/artifact/WeP5G98e96Y9T1gQibGqs1) puts Delete in the meeting's side panel, asks first, and says
the meeting is removed from ConXist only and not imported again. This is the second slice after the source page.

## Decision

- The meeting panel has Delete. It asks inside the panel, not in a second dialog stacked on it, and lists what
  happens: the transcript and summary leave ConXist for good, ConXist will not import the recording again, and the
  Plaud account keeps it. **Change from the mock:** the mock stacked an alert dialog over the panel. Two modal layers
  mean two focus traps fighting over Escape and Tab; asking in place keeps one, and Escape backs out of the question
  before it closes the panel. The wording is the mock's.
- An uploaded document is deleted the same way. Its stored file goes too, and the same file can be uploaded again.
- `DELETE /api/sources/{id}/documents/{documentId}`: 204, or 404 `document.not-found` when it is already gone (the
  app then treats it as deleted). Reads and deletes are logged by ids only.
- The delete and a `DocumentDeleted` event (ids only) are one transaction. The hub turns the item into a `REMOVED`
  marker with no title, rather than forgetting it, so a capture event delivered late cannot bring it back as waiting
  for review. Markers are never counted or shown.
- A deleted recording's Plaud file id goes in `deleted_recordings` (migration `V9__deleted_recordings.sql`, which
  also allows the hub's `REMOVED` status). Every check treats it as already handled, and inserting a meeting refuses
  it. A save and a delete of the same recording take turns (a PostgreSQL advisory lock on source and file id until
  the transaction ends), so a check importing the recording at the very moment it is deleted cannot bring it back.
  The import history counts it under "Already here".
- An upload's stored file is removed only when its key names the document's own workspace and source, and the file
  store refuses any key that leaves its folder.

## Consequences

- Nothing brings a deleted meeting back yet ("Import again" in the mock is a later slice). A deleted recording picked
  again in the recording picker counts as "Already here" without saying so.
- Agents and the MCP server get no delete tool: deleting customer data stays a person's action. When the tool set
  lands, a test will check that no tool reaches `DeleteDocument`.
- `deleted_recordings` rows and the hub's `REMOVED` markers (ids only) are kept for as long as the source exists.
  Deleting a source or a workspace, when it ships, removes them with it.
- A stored file that cannot be removed is logged by ids and left behind, encrypted (ADR 0004); nothing sweeps it yet.
- Migration V9 needs the owner's go before it runs anywhere but a developer's computer.
