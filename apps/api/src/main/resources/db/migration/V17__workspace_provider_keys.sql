-- Each workspace's own key per LLM provider (ADR 0014). The key is stored only encrypted (ADR 0004), bound to its
-- workspace and provider so a sealed key cannot be moved to another row. Only its last characters are kept in the
-- clear, to show which key it is. Like other modules' tables, no foreign key into the workspaces module (ADR 0002).

create table workspace_provider_keys (
    workspace_id uuid        not null,
    provider     text        not null check (length(provider) between 1 and 100),
    key_sealed   bytea       not null,
    key_ending   text        not null check (length(key_ending) between 1 and 4),
    added_at     timestamptz not null,
    checked_at   timestamptz not null,
    refused      boolean     not null,
    primary key (workspace_id, provider)
);
