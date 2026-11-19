-- A person picks which Plaud recordings to import when connecting (ADR 0005, "Picking recordings").

-- Whether recordings made after connecting are imported on their own. Existing connections keep doing so.
alter table plaud_account_connections add column import_new boolean not null default true;

-- Recordings a person picked that are not imported yet. Only Plaud's file id and recording time: no title, no text.
-- A row goes once the meeting is imported, was already there, or is no longer in Plaud.
create table plaud_recording_picks (
    source_id    uuid        not null,
    workspace_id uuid        not null,
    file_id      text        not null check (file_id ~ '^[A-Za-z0-9_-]{1,128}$'),
    recorded_at  timestamptz not null,
    picked_at    timestamptz not null,
    -- When a sync last found the transcript not ready; null until then. Those wait behind untried picks.
    tried_at     timestamptz,
    primary key (source_id, file_id),
    foreign key (workspace_id, source_id) references sources (workspace_id, id)
);
create index plaud_recording_picks_next on plaud_recording_picks (source_id, tried_at nulls first, recorded_at desc);
