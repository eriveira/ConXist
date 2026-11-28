-- Integrations become custom APIs (ADR 0027, owner 2026-11-28): a workspace adds any API by its https address and
-- sign-in, and its operations become tools. GitHub, Bitbucket and Gmail (ADR 0019 to 0021) are removed with their
-- rows and sealed tokens; they may come back later as templates of a custom API. Agents that picked their tools show
-- those picks as no longer allowed until a person removes them.
--
-- One row per API. The secret (key, token, or username and password) is stored only encrypted (ADR 0004), bound to
-- its row; only its last 4 characters are kept in the clear. The tools, at most 200, are kept as JSON with the API,
-- each with whether agents may use it. A logo a person chose is kept as a copy, fetched once or uploaded. Like other
-- modules' tables, no foreign key into the workspaces module (ADR 0002).

drop table integrations;

create table integrations (
    id                    uuid        primary key,
    workspace_id          uuid        not null,
    name                  text        not null check (length(name) between 1 and 60),
    address               text        not null check (length(address) between 8 and 2000),
    auth_kind             text        not null check (auth_kind in ('NONE', 'API_KEY', 'BEARER', 'BASIC')),
    auth_place            text        check (auth_place in ('HEADER', 'QUERY')),
    auth_name             text        check (length(auth_name) between 1 and 64),
    account               text        check (length(account) between 1 and 200),
    secret_ending         text        check (length(secret_ending) between 1 and 4),
    secret_sealed         bytea,
    check_path            text        check (length(check_path) between 1 and 500),
    status                text        not null check (status in ('WORKS', 'SIGN_IN_NEEDED', 'UNREACHABLE')),
    icon                  text,
    logo                  text,
    logo_address          text,
    logo_at               timestamptz,
    description_title     text,
    description_version   text,
    description_address   text,
    description_at        timestamptz,
    tools                 jsonb       not null,
    added_at              timestamptz not null,
    checked_at            timestamptz not null,
    constraint integrations_key_place check ((auth_kind = 'API_KEY') = (auth_place is not null and auth_name is not null)),
    constraint integrations_secret check ((auth_kind = 'NONE') = (secret_sealed is null)),
    constraint integrations_logo_whole check ((logo is null) = (logo_at is null) and (logo is not null or logo_address is null))
);

create unique index integrations_workspace_name on integrations (workspace_id, lower(name));

-- An MCP server's logo may now be an uploaded file, with no address (ADR 0027).
alter table mcp_servers drop constraint mcp_servers_logo_whole;
alter table mcp_servers add constraint mcp_servers_logo_whole check (
    (logo is null and logo_address is null and logo_at is null)
    or (logo is not null and logo_at is not null));
