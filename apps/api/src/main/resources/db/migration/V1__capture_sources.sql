-- Slice 1: sources, captured documents, hub items, and Spring Modulith's event publication registry.
-- Every table carries workspace_id so tenant isolation can be enforced once sign-in exists (ADR 0002).

create table sources (
    id            uuid         primary key,
    workspace_id  uuid         not null,
    provider      text         not null check (provider in
                      ('UPLOAD', 'PLAUD', 'EMAIL', 'GITHUB', 'BITBUCKET', 'R2', 'S3',
                       'GOOGLE_DRIVE', 'ICLOUD', 'ONEDRIVE', 'CONFLUENCE')),
    name          text         not null check (length(name) between 1 and 80),
    account_label text         not null default '' check (length(account_label) <= 120),
    added_at      timestamptz  not null
);
create unique index sources_workspace_name_uq on sources (workspace_id, lower(name));

create table captured_documents (
    id               uuid        primary key,
    workspace_id     uuid        not null,
    source_id        uuid        not null references sources (id),
    title            text        not null check (length(title) between 1 and 200),
    media_type       text        not null,
    size_bytes       bigint      not null check (size_bytes > 0),
    text_content     text        not null,
    origin           text        not null check (origin in ('UPLOAD', 'RECORDING')),
    origin_reference text        not null,
    captured_at      timestamptz not null,
    unique (source_id, origin_reference)
);
create index captured_documents_by_source on captured_documents (workspace_id, source_id, captured_at desc);

create table hub_items (
    item_id      uuid        primary key,
    workspace_id uuid        not null,
    item_type    text        not null check (item_type in ('SOURCE', 'DOCUMENT')),
    source_id    uuid        not null references sources (id),
    title        text        not null,
    status       text        not null check (status in ('CONNECTED', 'PENDING_REVIEW')),
    arrived_at   timestamptz not null
);
create index hub_items_latest on hub_items (workspace_id, arrived_at desc);

-- Copied from spring-modulith-events-jdbc 2.1.2 (schemas/v2/schema-postgresql.sql).
create table event_publication (
    id                     uuid                     not null primary key,
    listener_id            text                     not null,
    event_type             text                     not null,
    serialized_event       text                     not null,
    publication_date       timestamp with time zone not null,
    completion_date        timestamp with time zone,
    status                 text,
    completion_attempts    int,
    last_resubmission_date timestamp with time zone
);
create index event_publication_serialized_event_hash_idx on event_publication using hash (serialized_event);
create index event_publication_by_completion_date_idx on event_publication (completion_date);
