-- Where every agent run of a workspace stops (ADR 0023): the agent page no longer has its own limits. A workspace with
-- no row uses ConXist's defaults (6 model turns, 120 seconds, 10 tool calls). No foreign key into the workspaces
-- module (ADR 0002); a deleted workspace's row goes with its other runs data.

create table workspace_run_limits (
    workspace_id    uuid        primary key,
    max_steps       integer     not null check (max_steps between 1 and 20),
    timeout_seconds integer     not null check (timeout_seconds between 10 and 600),
    max_tool_calls  integer     not null check (max_tool_calls between 0 and 50),
    updated_at      timestamptz not null
);
