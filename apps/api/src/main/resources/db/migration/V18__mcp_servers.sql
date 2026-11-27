-- The MCP servers each workspace added for its agents (ADR 0017). The credential ConXist sends (nothing, a pasted
-- token, or the server's own sign-in with its refresh token) is stored only encrypted (ADR 0004), bound to its row.
-- The tools are the server's list with the ones agents may use, as JSON: names and descriptions come from the server.
-- Like other modules' tables, no foreign key into the workspaces module (ADR 0002).

create table mcp_servers (
    id                uuid        primary key,
    workspace_id      uuid        not null,
    name              text        not null check (length(name) between 1 and 60),
    address           text        not null check (length(address) between 1 and 2000),
    auth              text        not null check (auth in ('OAUTH', 'TOKEN', 'NONE')),
    icon              text        check (icon is null or length(icon) <= 140000),
    tools             jsonb       not null,
    status            text        not null check (status in ('WORKS', 'SIGN_IN_NEEDED', 'UNREACHABLE')),
    credential_sealed bytea       not null,
    added_at          timestamptz not null,
    checked_at        timestamptz not null
);

create unique index mcp_servers_workspace_name on mcp_servers (workspace_id, lower(name));
