-- Skills (ADR 0011): ways of working a team writes once and any agent follows. Like agents, a skill has a draft and
-- numbered versions that never change; an agent pins one version (in its definition JSON, so there is no foreign
-- key from agents). Built-in skills live in the code, not here.

create table skills (
    id                uuid        primary key,
    workspace_id      uuid        not null,
    source            text        not null check (source in ('YOURS', 'IMPORTED')),
    name              text        not null check (length(name) between 1 and 64),
    status            text        not null check (status in ('ACTIVE', 'ARCHIVED')),
    -- The definition being edited, as JSON (see JsonSkillDefinitions). Its name is also kept in the column above so
    -- the database can keep names unique.
    draft             jsonb       not null,
    revision          bigint      not null check (revision >= 1),
    published_version integer     not null default 0 check (published_version >= 0),
    draft_published   boolean     not null default false,
    created_at        timestamptz not null,
    updated_at        timestamptz not null
);
create unique index skills_name_uq on skills (workspace_id, lower(name));
create index skills_workspace_idx on skills (workspace_id, updated_at desc);

create table skill_versions (
    skill_id     uuid        not null references skills (id),
    number       integer     not null check (number >= 1),
    definition   jsonb       not null,
    note         text        not null default '' check (length(note) <= 140),
    published_at timestamptz not null,
    primary key (skill_id, number)
);
