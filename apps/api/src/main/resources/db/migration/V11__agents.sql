-- Agents (ADR 0010): a saved job description a person configures and publishes as numbered versions. The draft is
-- edited at any time; publishing copies it into agent_versions, which are never changed. Like the hub and the
-- workspaces read model, there is no foreign key into another module's tables (ADR 0002).

create table agents (
    id                uuid        primary key,
    workspace_id      uuid        not null,
    name              text        not null check (length(name) between 1 and 60),
    status            text        not null check (status in ('ACTIVE', 'ARCHIVED')),
    -- The definition being edited, as JSON (see JsonAgentDefinitions). Its name is also kept in the column above so
    -- the database can keep names unique.
    draft             jsonb       not null,
    -- Goes up by one on every change, so two tabs editing the same agent cannot overwrite each other.
    revision          bigint      not null check (revision >= 1),
    published_version integer     not null default 0 check (published_version >= 0),
    draft_published   boolean     not null default false,
    created_at        timestamptz not null,
    updated_at        timestamptz not null
);
create unique index agents_name_uq on agents (workspace_id, lower(name));
create index agents_workspace_idx on agents (workspace_id, updated_at desc);

create table agent_versions (
    agent_id     uuid        not null references agents (id),
    number       integer     not null check (number >= 1),
    definition   jsonb       not null,
    published_at timestamptz not null,
    primary key (agent_id, number)
);
