-- Where a workspace's new blank agents start (ADR 0014). A workspace with no row starts them on the API's balanced
-- setting. Like other modules' tables, no foreign key into the workspaces module (ADR 0002).

create table workspace_model_defaults (
    workspace_id uuid        primary key,
    provider     text        not null check (length(provider) between 1 and 100),
    model        text        not null check (length(model) between 1 and 100),
    effort       text        not null check (effort in ('LOW', 'MEDIUM', 'HIGH')),
    updated_at   timestamptz not null
);
