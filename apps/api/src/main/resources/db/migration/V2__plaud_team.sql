-- Plaud Team connections (Enterprise API). ConXist signs in with its own key pair; the Team's admin registers the
-- public key with Plaud. Private keys are stored only encrypted (AES-GCM, key from CONXIST_SECRETS_KEY).

create table recorder_keys (
    key_id             text        primary key check (key_id ~ '^cx-[0-9a-f]{16}$'),
    workspace_id       uuid        not null,
    public_key_pem     text        not null,
    private_key_sealed bytea       not null,
    created_at         timestamptz not null,
    unique (workspace_id, key_id)
);

create table plaud_team_connections (
    source_id         uuid        primary key,
    workspace_id      uuid        not null,
    client_id         text        not null check (length(client_id) between 1 and 200),
    key_id            text        not null,
    team_name         text        not null check (length(team_name) <= 200),
    start_from        text        not null check (start_from in ('LAST_30_DAYS', 'LAST_7_DAYS', 'NEW_ONLY')),
    start_cutoff      timestamptz not null,
    include_summaries boolean     not null,
    -- Where the last complete read of Plaud's change feed ended; null until the first one.
    feed_checkpoint   text,
    status            text        not null check (status in ('ACTIVE', 'PAUSED')),
    last_checked_at   timestamptz,
    connected_at      timestamptz not null,
    foreign key (workspace_id, source_id) references sources (workspace_id, id),
    foreign key (workspace_id, key_id) references recorder_keys (workspace_id, key_id)
);
create index plaud_team_connections_by_workspace on plaud_team_connections (workspace_id, connected_at);
