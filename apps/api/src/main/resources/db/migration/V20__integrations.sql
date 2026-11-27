-- The integrations each workspace connected (ADR 0019): tools ConXist reaches through their own API, GitHub first.
-- One row per workspace and kind. The token is stored only encrypted (ADR 0004), bound to its row; only its last 4
-- characters are kept in the clear, to show which token it is. Repositories are the ones the token reached when last
-- checked, and allowed_tools the ConXist tools agents may use, both as JSON. Like other modules' tables, no foreign
-- key into the workspaces module (ADR 0002).

create table integrations (
    id             uuid        primary key,
    workspace_id   uuid        not null,
    kind           text        not null check (kind in ('GITHUB')),
    account        text        not null check (length(account) between 1 and 100),
    repositories   jsonb       not null,
    token_ending   text        not null check (length(token_ending) between 1 and 4),
    allowed_tools  jsonb       not null,
    status         text        not null check (status in ('WORKS', 'SIGN_IN_NEEDED', 'UNREACHABLE')),
    token_sealed   bytea       not null,
    added_at       timestamptz not null,
    checked_at     timestamptz not null
);

create unique index integrations_workspace_kind on integrations (workspace_id, kind);
