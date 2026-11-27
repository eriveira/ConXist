-- A logo a person chose for an MCP server by its address (ADR 0022). ConXist fetched it once and keeps its copy here,
-- so it still shows if that address changes; null shows the logo ConXist found (the icon column).
alter table mcp_servers
    add column logo text,
    add column logo_address text,
    add column logo_at timestamptz,
    add constraint mcp_servers_logo_whole check (
        (logo is null and logo_address is null and logo_at is null)
        or (logo is not null and logo_address is not null and logo_at is not null));
