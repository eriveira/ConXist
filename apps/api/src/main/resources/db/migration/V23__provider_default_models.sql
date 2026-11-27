-- Each LLM provider gets its own default model in a workspace (owner, 2026-11-27). The row that was the workspace's
-- one default becomes its provider's default, and new agents keep starting on that provider.

alter table workspace_model_defaults drop constraint workspace_model_defaults_pkey;
alter table workspace_model_defaults add primary key (workspace_id, provider);
alter table workspace_model_defaults add column new_agents boolean not null default false;
update workspace_model_defaults set new_agents = true;
