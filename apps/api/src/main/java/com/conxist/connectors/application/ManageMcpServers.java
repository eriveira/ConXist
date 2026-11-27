package com.conxist.connectors.application;

import java.net.URI;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import com.conxist.agents.AgentBriefs;
import com.conxist.connectors.application.McpConnections.Connection;
import com.conxist.connectors.domain.InvalidMcp;
import com.conxist.connectors.domain.McpServer;
import com.conxist.platform.Conflict;
import com.conxist.platform.CurrentWorkspace;
import com.conxist.platform.NotFound;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The MCP servers a workspace added (ADR 0017): add one from a ready connection, rename it and tick the tools agents
 * may use, check it again for new tools, sign in again, and remove it. A tool or a server that agents use stays: the
 * person removes it from those agents first.
 */
public final class ManageMcpServers {

    /** Who changed what, and when: the workspace, the server and the action; never a credential. */
    private static final Logger log = LoggerFactory.getLogger(ManageMcpServers.class);
    public static final int MAX_SERVERS = 20;

    private final McpServerRepository servers;
    private final McpConnections connections;
    private final McpCredentials credentials;
    private final McpGateway gateway;
    private final McpServerViews views;
    private final CurrentWorkspace workspace;
    private final Clock clock;

    public ManageMcpServers(McpServerRepository servers, McpConnections connections, McpCredentials credentials,
            McpGateway gateway, McpServerViews views, CurrentWorkspace workspace, Clock clock) {
        this.servers = servers;
        this.connections = connections;
        this.credentials = credentials;
        this.gateway = gateway;
        this.views = views;
        this.workspace = workspace;
        this.clock = clock;
    }

    public List<McpViews.ServerView> list() {
        Map<UUID, Map<String, List<AgentBriefs.McpToolUse>>> uses = views.uses();
        return servers.findAll(workspace.id()).stream()
                .map(server -> views.server(server, uses.getOrDefault(server.id(), Map.of()))).toList();
    }

    public McpViews.ServerView get(UUID serverId) {
        return views.server(load(serverId));
    }

    /** Adds the server a ready connection reached, with the tools the person ticked allowed. */
    public McpViews.ServerView add(UUID connectionId, String name, Set<String> allowed) {
        UUID workspaceId = workspace.id();
        Connection connection = ready(workspaceId, connectionId);
        if (connection.serverId() != null) {
            throw new InvalidMcp("connection-for-server", "This connection signs in an added server again.");
        }
        if (servers.findAll(workspaceId).size() >= MAX_SERVERS) {
            throw new InvalidMcp("too-many", "A workspace can add up to " + MAX_SERVERS + " MCP servers.");
        }
        McpServer server = McpServer.add(UUID.randomUUID(), workspaceId, name, connection.address(),
                connection.credential().auth(), connection.facts().icon(), connection.facts().tools(), allowed,
                clock.instant());
        if (!servers.insertIfNameFree(server, connection.credential())) {
            throw nameTaken(server.name());
        }
        connections.forget(connectionId);
        log.info("Workspace {} added MCP server {} ({} of {} tools allowed)", workspaceId, server.id(),
                allowed.size(), server.tools().size());
        return views.server(server);
    }

    /** Renames the server and allows exactly the ticked tools. A tool agents use can't be unticked. */
    public McpViews.ServerView update(UUID serverId, String name, Set<String> allowed) {
        McpServer server = load(serverId);
        Set<String> unticked = server.tools().stream().filter(tool -> tool.allowed() && !allowed.contains(tool.name()))
                .map(tool -> tool.name()).collect(Collectors.toSet());
        List<AgentBriefs.McpToolUse> blocking = views.usesOf(serverId).stream()
                .filter(use -> unticked.contains(use.tool())).toList();
        if (!blocking.isEmpty()) {
            throw new Conflict("mcp.tool-in-use", "An agent uses " + blocking.getFirst().tool()
                    + ". Remove it from " + agentNames(blocking) + " first.");
        }
        McpServer changed = server.renamed(name == null ? server.name() : name).allow(allowed);
        if (!servers.update(changed)) {
            // Nothing changed: either the name is taken, or the server was removed a moment ago.
            load(serverId);
            throw nameTaken(changed.name());
        }
        log.info("Workspace {} changed MCP server {} ({} tools allowed)", server.workspaceId(), serverId,
                allowed.size());
        return views.server(changed);
    }

    /** Asks the server again: is it answering, and which tools does it list now? New tools start off. */
    public McpViews.CheckView check(UUID serverId) {
        McpServer server = load(serverId);
        try {
            McpGateway.ServerFacts facts = listWithRefresh(server);
            List<String> added = server.newIn(facts.tools());
            McpServer listed = server.listed(facts.tools(), facts.icon(), clock.instant());
            servers.update(listed);
            log.info("Workspace {} checked MCP server {}: works, {} new tools", server.workspaceId(), serverId,
                    added.size());
            return new McpViews.CheckView(true, null, added, views.server(listed));
        } catch (McpGateway.SignInRefused refused) {
            return failed(server, McpServer.Status.SIGN_IN_NEEDED, "sign-in-needed");
        } catch (McpGateway.NotMcp notMcp) {
            return failed(server, McpServer.Status.UNREACHABLE, "not-mcp");
        } catch (McpGateway.Unreachable | McpGateway.AddressRefused unreachable) {
            return failed(server, McpServer.Status.UNREACHABLE, "unreachable");
        }
    }

    /** Saves the credential a connection for this server got, and the tools the server lists with it. */
    public McpViews.ServerView signedInAgain(UUID serverId, UUID connectionId) {
        McpServer server = load(serverId);
        Connection connection = ready(server.workspaceId(), connectionId);
        if (!serverId.equals(connection.serverId())) {
            throw new InvalidMcp("connection-for-server", "This connection is for another server.");
        }
        servers.saveCredential(server.workspaceId(), serverId, connection.credential());
        McpServer listed = server.signedInWith(connection.credential().auth())
                .listed(connection.facts().tools(), connection.facts().icon(), clock.instant());
        servers.update(listed);
        connections.forget(connectionId);
        log.info("Workspace {} signed in to MCP server {} again", server.workspaceId(), serverId);
        return views.server(listed);
    }

    /**
     * Shows the image at this address as the server's logo (ADR 0022). ConXist fetches it once, through the same guard
     * as a server's address and without any token, and keeps its own copy.
     */
    public McpViews.ServerView useLogo(UUID serverId, String address) {
        McpServer server = load(serverId);
        String image;
        URI at;
        try {
            at = gateway.checked(address == null ? "" : address.strip());
            image = gateway.image(at);
        } catch (McpGateway.AddressRefused refused) {
            throw switch (refused.code()) {
                case "address-not-https" -> new InvalidMcp("logo-not-https", "Use an address that starts with https://.");
                case "address-private" -> new InvalidMcp("logo-private",
                        "This address is on a private network. ConXist only fetches logos from the internet.");
                default -> new InvalidMcp("logo-address-invalid", "This does not look like an image address.");
            };
        } catch (McpGateway.Unreachable unreachable) {
            throw new InvalidMcp("logo-unreachable", "ConXist could not reach this address. Check it and try again.");
        }
        if (image == null) {
            throw new InvalidMcp("logo-not-image",
                    "That address isn't an image. Use the address of a PNG, JPG, SVG, WebP or ICO up to 100 KB.");
        }
        McpServer changed = server.withLogo(new McpServer.Logo(image, at, clock.instant()));
        saveLogo(changed);
        log.info("Workspace {} chose a logo for MCP server {}", server.workspaceId(), serverId);
        return views.server(changed);
    }

    /** Goes back to the logo ConXist found: the server's own, else its site's. */
    public McpViews.ServerView dropLogo(UUID serverId) {
        McpServer changed = load(serverId).withLogo(null);
        saveLogo(changed);
        log.info("Workspace {} went back to the found logo for MCP server {}", changed.workspaceId(), serverId);
        return views.server(changed);
    }

    private void saveLogo(McpServer server) {
        if (!servers.saveLogo(server.workspaceId(), server.id(), server.logo())) {
            throw new NotFound("mcp.not-found", "This MCP server is no longer in this workspace.");
        }
    }

    /** The agents that use the server's tools; while there are any, it can't be removed. */
    public List<McpViews.AgentUseView> uses(UUID serverId) {
        load(serverId);
        Map<UUID, AgentBriefs.McpToolUse> byAgent = new LinkedHashMap<>();
        views.usesOf(serverId).forEach(use -> byAgent.putIfAbsent(use.agentId(), use));
        return byAgent.values().stream().map(use -> new McpViews.AgentUseView(use.agentId(), use.agentName(),
                use.where(), use.archived())).toList();
    }

    public void remove(UUID serverId) {
        McpServer server = load(serverId);
        List<AgentBriefs.McpToolUse> uses = views.usesOf(serverId);
        if (!uses.isEmpty()) {
            throw new Conflict("mcp.in-use", "Agents use this server's tools. Remove them from "
                    + agentNames(uses) + " first.");
        }
        servers.delete(server.workspaceId(), serverId);
        log.info("Workspace {} removed MCP server {}", server.workspaceId(), serverId);
    }

    private McpGateway.ServerFacts listWithRefresh(McpServer server) {
        try {
            return gateway.list(server.address(), credentials.bearer(server));
        } catch (McpGateway.SignInRefused refused) {
            return gateway.list(server.address(), credentials.refreshed(server));
        }
    }

    private McpViews.CheckView failed(McpServer server, McpServer.Status status, String code) {
        McpServer marked = server.withStatus(status, clock.instant());
        servers.update(marked);
        log.info("Workspace {} checked MCP server {}: {}", server.workspaceId(), server.id(), code);
        return new McpViews.CheckView(false, code, List.of(), views.server(marked));
    }

    private Connection ready(UUID workspaceId, UUID connectionId) {
        Connection connection = connections.find(workspaceId, connectionId)
                .orElseThrow(() -> new NotFound("mcp.connection-gone", "This connection expired. Start again."));
        if (connection.status() != McpConnections.Status.READY) {
            throw new InvalidMcp("connection-not-ready", "Connect to the server first.");
        }
        return connection;
    }

    private McpServer load(UUID serverId) {
        return servers.find(workspace.id(), serverId)
                .orElseThrow(() -> new NotFound("mcp.not-found", "This MCP server is no longer in this workspace."));
    }

    private static String agentNames(List<AgentBriefs.McpToolUse> uses) {
        Set<String> names = new LinkedHashSet<>();
        uses.forEach(use -> names.add(use.agentName()));
        return String.join(", ", names);
    }

    private static InvalidMcp nameTaken(String name) {
        return new InvalidMcp("name-taken", "An MCP server named \"" + name + "\" is already in this workspace.");
    }
}
