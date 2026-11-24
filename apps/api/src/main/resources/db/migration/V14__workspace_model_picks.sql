-- The model a workspace picked for each preset (ADR 0014). A preset with no row runs on the default in the API's
-- settings. Like other modules' tables, no foreign key into the workspaces module (ADR 0002).

create table workspace_model_picks (
    workspace_id uuid        not null,
    preset       text        not null check (preset in ('FAST', 'BALANCED', 'THOROUGH')),
    model        text        not null check (length(model) between 1 and 100),
    updated_at   timestamptz not null,
    primary key (workspace_id, preset)
);
