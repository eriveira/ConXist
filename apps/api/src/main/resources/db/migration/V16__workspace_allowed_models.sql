-- The models a workspace ticked or unticked in Settings (ADR 0014). A model with no row is allowed when the API's
-- settings list it. The label is the name the provider gave the model, kept so allowed models show without asking it.
-- Like other modules' tables, no foreign key into the workspaces module (ADR 0002).

create table workspace_allowed_models (
    workspace_id uuid        not null,
    provider     text        not null check (length(provider) between 1 and 100),
    model        text        not null check (length(model) between 1 and 100),
    label        text        not null check (length(label) between 1 and 200),
    allowed      boolean     not null,
    updated_at   timestamptz not null,
    primary key (workspace_id, provider, model)
);
