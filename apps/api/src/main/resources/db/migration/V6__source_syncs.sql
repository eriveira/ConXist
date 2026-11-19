-- Import history: one row per check of a meeting recorder, kept 90 days. Counts and a problem code only: no titles,
-- no text and no Plaud ids, so the history holds no customer content.
create table source_syncs (
    id               uuid        primary key,
    workspace_id     uuid        not null,
    source_id        uuid        not null,
    started_at       timestamptz not null,
    finished_at      timestamptz not null,
    started_by       text        not null check (started_by in ('SCHEDULE', 'PERSON')),
    outcome          text        not null check (outcome in ('IMPORTED', 'UP_TO_DATE', 'FAILED')),
    imported         integer     not null check (imported >= 0),
    already_imported integer     not null check (already_imported >= 0),
    waiting          integer     not null check (waiting >= 0),
    queued           integer     not null check (queued >= 0),
    problem_code     text        check (problem_code ~ '^[a-z][a-z.-]{0,63}$'),
    foreign key (workspace_id, source_id) references sources (workspace_id, id)
);
create index source_syncs_by_source on source_syncs (workspace_id, source_id, started_at desc);
create index source_syncs_by_age on source_syncs (started_at);
