# 0006. A page per source: meetings, one meeting, import history

- Status: **Proposed** (accepted when the owner merges the pull request that brings it)
- Date: 2026-11-19
- Decided by: Eugenio Riviera (asked for import history per source and a view of each transcript, 2026-11-19;
  accepted the build order "view and history, then delete, then edit")

## Context

People could see how many meetings a source held, but not which ones, not their text, and not what each check
of Plaud did. The mock (`App/mocks`, "ConXist Meetings Mock") puts one page per source with two tabs, Meetings and
Import history, and opens a meeting in a side panel. Delete and edit are later slices.

## Decision

- `#/sources/{id}` is a source's page. The Sources card links to it ("Open").
- Meetings: `GET /api/sources/{id}/documents` now also gives `recordedAt` (read from the meeting text), so the list
  groups by the day a meeting took place. A search by title runs in the browser.
- One meeting: `GET /api/sources/{id}/documents/{documentId}` returns the text, and for a recording the meeting split
  into summary and transcript lines (`MeetingText.read`, the reverse of `compose`). `Cache-Control: no-store`. The
  web app shows it as plain text, never as markup: it is customer data and untrusted.
- Import history: every check of a meeting recorder, by the schedule or by a person, is written to `source_syncs`
  (migration `V6__source_syncs.sql`): times, who started it, outcome, counts and a problem code. No titles, no text,
  no Plaud ids. Rows older than 90 days are deleted on each scheduled run. `GET /api/sources/{id}/syncs` returns the
  newest 200, by default only checks that imported something or failed (`?all=true` for every check).

## Data

`source_syncs` adds about 288 rows a day per connected recorder (one check every 5 minutes), so about 26,000 rows
per recorder at the 90-day limit. Migration V6 needs the owner's go before it is applied anywhere but a developer's
computer.

## Consequences

- The history did not name the meetings a check imported; the amendment below adds that.
- A transcript line with no speaker whose text starts with "Word: " reads back as said by "Word". Plaud gives a
  speaker on every line it transcribes, so this has not been seen.
- Delete and edit (slices 2 and 3) build on this page and the side panel.

## Amendment, 2026-11-19: closing the gaps with the agreed mock

The owner asked to match the agreed mocks and to close the gaps that need the API before edit starts. No migration.

- Meeting length: the import writes it on the meeting text's first line,
  `Recorded yyyy-MM-dd HH:mm UTC · Language: xx · Length: hh:mm:ss`, when Plaud gives it. `MeetingText.read` reads it
  back. The documents list and the meeting view return `lengthSeconds`; meetings imported before show none. It lives
  in the encrypted text instead of a column so no migration is needed; if a query ever needs it, it moves to a column.
  Plaud Team imports (on hold) carry no length.
- Speaker count: the documents list returns `speakerCount`, read from the transcript's lines. The list now reads each
  meeting's text; at today's sizes that is cheap. Columns are the fix if it is not.
- Meetings per check: the history's last column, "Meetings or problem", names up to three meetings each check
  imported, and a name opens the meeting. The web app links a meeting to a check when it was imported between the
  check's start and end, and never names more than the check's own count. Two checks can overlap (the schedule and a
  person), so in that case a name can belong to the other check. Storing the check's id on each imported document
  is exact and needs a migration: REFACTOR LATER, with the owner's go.
- First sync: the web app labels as "First sync" the earliest check a person started within five minutes of the
  source being added (the connect dialog starts it). The API keeps `SCHEDULE` and `PERSON` only.
- The Plaud picker marks recordings this workspace already holds ("Already in ConXist", not pickable; the API refuses
  them too with `plaud.pick-already-held`) and ones a person deleted here ("Deleted in ConXist", information only).
  The recording list carries `inConxist` (`HELD`, `DELETED` or null), looked up for this workspace only.
- "Still transcribing" on picker rows is not built: Plaud's recording list does not say whether a transcript is
  ready, and asking per recording would mean up to 500 calls per list.
