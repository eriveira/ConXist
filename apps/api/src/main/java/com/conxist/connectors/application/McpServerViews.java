package com.conxist.connectors.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import com.conxist.agents.AgentBriefs;
import com.conxist.connectors.domain.McpServer;
import com.conxist.connectors.domain.McpTool;

/** Builds what the app shows, with the agents that use each tool. */
public final class McpServerViews {

    private final AgentBriefs agents;

    public McpServerViews(AgentBriefs agents) {
        this.agents = agents;
    }

    /** The current workspace's agents' uses of MCP tools, by server and tool. */
    Map<UUID, Map<String, List<AgentBriefs.McpToolUse>>> uses() {
        return agents.mcpToolUses().stream().collect(Collectors.groupingBy(AgentBriefs.McpToolUse::serverId,
                Collectors.groupingBy(AgentBriefs.McpToolUse::tool)));
    }

    List<AgentBriefs.McpToolUse> usesOf(UUID serverId) {
        return agents.mcpToolUses().stream().filter(use -> use.serverId().equals(serverId)).toList();
    }

    McpViews.ServerView server(McpServer server) {
        return server(server, uses().getOrDefault(server.id(), Map.of()));
    }

    McpViews.ServerView server(McpServer server, Map<String, List<AgentBriefs.McpToolUse>> uses) {
        return new McpViews.ServerView(server.id(), server.name(), server.address(), server.auth(), server.shownIcon(),
                server.logo() == null ? null : new McpViews.LogoView(server.logo().address(), server.logo().fetchedAt()),
                server.status(), server.tools().stream().map(tool -> tool(tool, uses.getOrDefault(tool.name(),
                        List.of()))).toList(), server.addedAt(), server.checkedAt());
    }

    McpViews.ConnectionView connection(McpConnections.Connection connection) {
        McpGateway.ServerFacts facts = connection.facts();
        List<McpViews.ToolView> tools = facts == null ? List.of()
                : facts.tools().stream().map(tool -> tool(tool, List.of())).toList();
        return new McpViews.ConnectionView(connection.id(), connection.serverId(), connection.status(),
                connection.signInUrl(), connection.credential() == null ? null : connection.credential().auth(),
                facts == null ? null : suggestedName(connection, facts), facts == null ? null : facts.icon(), tools,
                connection.error());
    }

    static McpViews.ToolView tool(McpTool tool, List<AgentBriefs.McpToolUse> uses) {
        return new McpViews.ToolView(tool.name(), tool.title(), tool.description(), tool.readOnly(), tool.allowed(),
                uses.stream().map(use -> new McpViews.AgentUseView(use.agentId(), use.agentName(), use.where(),
                        use.archived())).toList());
    }

    /** The name the server shows people, else its own name, else its host; cut to what a name may hold. */
    private static String suggestedName(McpConnections.Connection connection, McpGateway.ServerFacts facts) {
        String name = !blank(facts.title()) ? facts.title() : !blank(facts.name()) ? facts.name()
                : connection.address().getHost();
        name = name.strip();
        return name.codePointCount(0, name.length()) <= McpServer.MAX_NAME_LENGTH ? name
                : name.substring(0, name.offsetByCodePoints(0, McpServer.MAX_NAME_LENGTH)).strip();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
