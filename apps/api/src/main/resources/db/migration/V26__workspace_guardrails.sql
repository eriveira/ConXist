-- What a workspace adds to ConXist's own guardrail (ADR 0025): rules every agent of the workspace follows after
-- ConXist's rule and before its own instructions, and the most text one call to an outside tool may send. A workspace
-- with no row has no rules of its own and uses ConXist's limit (4,000 characters). No foreign key into the workspaces
-- module (ADR 0002); a deleted workspace's row goes with its other runs data.

create table workspace_guardrails (
    workspace_id         uuid        primary key,
    rules                text        not null check (char_length(rules) <= 2000),
    max_input_characters integer     not null check (max_input_characters between 200 and 4000),
    updated_at           timestamptz not null
);
