-- A person can keep several workspaces (ADR 0007). Every request names the one it acts for.

create table workspaces (
    id          uuid        primary key,
    name        text        not null check (length(name) between 1 and 80),
    description text        not null default '' check (length(description) <= 500),
    created_at  timestamptz not null
);
create unique index workspaces_name_uq on workspaces (lower(name));

-- The workspaces module's read model of which sources each workspace has, fed by SourceAdded. Like hub_items it
-- has no foreign key into another module's tables (ADR 0002).
create table workspace_sources (
    workspace_id uuid not null references workspaces (id),
    source_id    uuid not null,
    primary key (workspace_id, source_id)
);

-- Data made before workspaces existed belongs to the one configured workspace: give it a row, so it stays
-- reachable (sources, and Plaud Team keys made before any source). A database with neither gets no row, and the
-- app asks for a first workspace.
insert into workspaces (id, name, created_at)
select workspace_id,
       case when position = 1 then 'My workspace' else 'My workspace ' || position end,
       first_seen
from (select workspace_id, min(seen) as first_seen, row_number() over (order by min(seen), workspace_id) as position
      from (select workspace_id, added_at as seen from sources
            union all
            select workspace_id, created_at as seen from recorder_keys) owned
      group by workspace_id) existing;

insert into workspace_sources (workspace_id, source_id)
select workspace_id, id from sources;
