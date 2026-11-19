-- Recordings a person deleted from ConXist on purpose, so a later check of the recorder does not import them again.
-- Only the recorder's file id is kept: no title, no text.
create table deleted_recordings (
    workspace_id     uuid        not null,
    source_id        uuid        not null,
    origin_reference text        not null check (length(origin_reference) between 1 and 200),
    deleted_at       timestamptz not null,
    primary key (source_id, origin_reference),
    foreign key (workspace_id, source_id) references sources (workspace_id, id)
);

-- The hub keeps a marker for a deleted document instead of forgetting it, so a capture event delivered late (a retry,
-- or a replay after a restart) cannot bring it back as waiting for review. The marker holds no title.
alter table hub_items drop constraint hub_items_status_check;
alter table hub_items add constraint hub_items_status_check
    check (status in ('CONNECTED', 'PENDING_REVIEW', 'REMOVED'));
