package com.conxist.connectors.application;

import java.util.UUID;
import com.conxist.agents.AgentMcpTools;
import com.conxist.platform.CurrentWorkspace;

/** Answers the agents module whether the current workspace still allows a server's or an integration's tool. */
public final class AllowedMcpTools implements AgentMcpTools {

    private final McpServerRepository servers;
    private final IntegrationRepository integrations;
    private final CurrentWorkspace workspace;

    public AllowedMcpTools(McpServerRepository servers, IntegrationRepository integrations,
            CurrentWorkspace workspace) {
        this.servers = servers;
        this.integrations = integrations;
        this.workspace = workspace;
    }

    /** The id is an MCP server's or an integration's (ADR 0019): agents pick both the same way. */
    @Override
    public boolean allowed(UUID serverId, String tool) {
        return servers.find(workspace.id(), serverId).map(server -> server.allows(tool))
                .or(() -> integrations.find(workspace.id(), serverId).map(integration -> integration.allows(tool)))
                .orElse(false);
    }
}
