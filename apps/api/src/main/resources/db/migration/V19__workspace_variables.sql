-- A workspace's variables (ADR 0018): names and values its agents and skills use as {{name}}. They replace topics,
-- which nothing read yet: a workspace that had topics gets one variable, "topics", listing them (cut at 2,000
-- characters). The old table is kept, renamed, until a later migration drops it, so nothing is lost if the list was
-- longer. Names are unique in a workspace, ignoring case. The 100-variable limit and the name rule are kept by the app.

create table workspace_variables (
    id           uuid        primary key,
    workspace_id uuid        not null references workspaces (id),
    name         text        not null check (length(name) between 1 and 60),
    value        text        not null check (length(value) between 1 and 2000),
    created_at   timestamptz not null
);
create unique index workspace_variables_name_uq on workspace_variables (workspace_id, lower(name));

insert into workspace_variables (id, workspace_id, name, value, created_at)
select gen_random_uuid(), workspace_id, 'topics',
       left(string_agg(case when description = '' then name else name || ' (' || description || ')' end, '; '
                       order by lower(name)), 2000),
       min(created_at)
from workspace_topics
group by workspace_id;

alter table workspace_topics rename to workspace_topics_v13;
