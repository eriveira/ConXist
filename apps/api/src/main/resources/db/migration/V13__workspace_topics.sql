-- A workspace's topics (ADR 0014): the subjects the topic classifier sorts meetings into. Names are unique in a
-- workspace, ignoring case. The 50-topic limit is kept by the app.

create table workspace_topics (
    id           uuid        primary key,
    workspace_id uuid        not null references workspaces (id),
    name         text        not null check (length(name) between 1 and 60),
    description  text        not null default '' check (length(description) <= 300),
    created_at   timestamptz not null
);
create unique index workspace_topics_name_uq on workspace_topics (workspace_id, lower(name));
