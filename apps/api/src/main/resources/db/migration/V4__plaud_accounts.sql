-- A person's own Plaud account, connected by signing in on Plaud's page (OAuth through Plaud's MCP server,
-- ADR 0005). Tokens are stored only encrypted (AES-GCM, key from CONXIST_SECRETS_KEY).

-- The client registration ConXist received from an authorization server (OAuth dynamic client registration).
create table oauth_clients (
    issuer        text        not null,
    redirect_uri  text        not null,
    client_id     text        not null check (length(client_id) between 1 and 2000),
    registered_at timestamptz not null,
    primary key (issuer, redirect_uri)
);

-- One sign-in from start to finish. The PKCE verifier and, once signed in, the tokens are sealed; the row is used
-- by at most one source, and expires when it is not finished in time.
create table plaud_sign_ins (
    id              uuid        primary key,
    workspace_id    uuid        not null,
    state           text        not null unique check (length(state) between 32 and 128),
    verifier_sealed bytea       not null,
    status          text        not null check (status in ('WAITING', 'SIGNED_IN', 'CANCELLED', 'FAILED', 'USED')),
    account_label   text        check (length(account_label) <= 200),
    tokens_sealed   bytea,
    started_at      timestamptz not null,
    finished_at     timestamptz
);
create index plaud_sign_ins_by_workspace on plaud_sign_ins (workspace_id, started_at);

create table plaud_account_connections (
    source_id         uuid        primary key,
    workspace_id      uuid        not null,
    account_label     text        not null check (length(account_label) <= 200),
    start_from        text        not null check (start_from in ('LAST_30_DAYS', 'LAST_7_DAYS', 'NEW_ONLY')),
    start_cutoff      timestamptz not null,
    include_summaries boolean     not null,
    -- When the last complete sync started; null until the first one.
    read_through      timestamptz,
    status            text        not null check (status in ('ACTIVE', 'PAUSED')),
    last_checked_at   timestamptz,
    connected_at      timestamptz not null,
    foreign key (workspace_id, source_id) references sources (workspace_id, id)
);
create index plaud_account_connections_by_workspace on plaud_account_connections (workspace_id, connected_at);

-- The current tokens of a connected account, replaced on every refresh.
create table plaud_account_tokens (
    source_id     uuid        primary key,
    workspace_id  uuid        not null,
    tokens_sealed bytea       not null,
    updated_at    timestamptz not null,
    foreign key (workspace_id, source_id) references sources (workspace_id, id)
);
