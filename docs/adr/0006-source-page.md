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

- The history does not name the meetings a check imported. The Meetings tab shows when each was imported.
- A transcript line with no speaker whose text starts with "Word: " reads back as said by "Word". Plaud gives a
  speaker on every line it transcribes, so this has not been seen.
- Delete and edit (slices 2 and 3) build on this page and the side panel.
