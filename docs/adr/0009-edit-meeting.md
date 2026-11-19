# 0009. Correct a meeting in ConXist, and put back the original

- Status: **Proposed** (needs the owner's go before migration V10 is applied anywhere shared)
- Date: 2026-11-19
- Decided by: Eugenio Riviera (meetings mock approved; build order "view and history, then delete, then edit";
  "Before edit": close the mock gaps first, then build edit, project thread, 2026-11-19)
- Extends: ADR 0006 (the source page), ADR 0008 (delete)

## Context

Plaud gets names and words wrong ("Con exist" for ConXist, "Speaker 1" for a person). The mock
(https://claude.ai/artifact/WeP5G98e96Y9T1gQibGqs1) puts Edit in the meeting panel: the title, a new name per
speaker that applies to every line, and each line's text, with an "Unsaved changes" bar, a "Discard your changes?"
question and an "Edited" tag. Plaud is never changed: ConXist only reads from a source.

## Decision

- `PUT /api/sources/{id}/documents/{documentId}/meeting` takes the title, speaker renames (old name to new name) and
  line fixes (position to new text), with `editedAt`: the edit the person started from (null when it is as imported).
  An edit from an older read is refused with `document.changed`, so a second tab never overwrites a newer edit.
  Times, the order of lines, who said each line and the summaries stay as recorded. The new text is cleaned the same
  way an import is (one line per utterance, safe speaker names) and must fit the same size limit.
- Words typed on a line with no speaker that start like "Name: " are written as "Name - ...", so an edit (or a
  Plaud line) can never make a person appear to say something. JSON bodies over 4 MB, or without a stated length,
  are refused before they are read; an edit sends at most 500 changed lines of 5,000 characters.
- `POST .../restore` with `editedAt` puts back the title and text as imported.
- The first edit keeps the imported title and text (`original_title`, `original_sealed`), sealed with the document's
  key and its own associated data so it cannot be swapped with the current text. Restore clears them.
- A changed title is sent to the hub (`DocumentRetitled`). Logs name ids only, never a title or text.
- Only recorded meetings can be edited; an uploaded file is re-uploaded instead. No tool for agents or MCP edits a
  meeting: a person does.
- The app: Edit (pencil) in the panel opens the fields in place; Cancel, Close and Escape ask "Discard your changes?"
  when something changed; Save shows the meeting with "Edited in ConXist on ..." and a "Restore original" link that
  asks first. Rows show an "Edited" tag. **Change from the mock:** the discard question sits inside the panel, as the
  delete question does (ADR 0008), not in a second dialog; and "Restore original" is added, which the mock did not show.

## Data

Migration `V10__document_edits.sql` adds `edited_at`, `original_title` and `original_sealed` to `captured_documents`,
with a check that the three are set together. Nothing else changes. V10 needs the owner's go before it is applied
anywhere but a developer's computer.

## Consequences

- An edited meeting keeps at most two copies of its text (now and as imported).
- A sync never overwrites an edit: a meeting already in ConXist is not imported again.
- Find, copy and the meeting's length and speaker count use the edited text.
