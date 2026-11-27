package com.conxist.connectors.application;

import java.util.UUID;
import com.conxist.agents.AgentMcpTools;
import com.conxist.platform.CurrentWorkspace;

/** Answers the agents module whether the current workspace still allows a server's tool (ADR 0017). */
public final class AllowedMcpTools implements AgentMcpTools {

    private final McpServerRepository servers;
    private final CurrentWorkspace workspace;

    public AllowedMcpTools(McpServerRepository servers, CurrentWorkspace workspace) {
        this.servers = servers;
        this.workspace = workspace;
    }

    @Override
    public boolean allowed(UUID serverId, String tool) {
        return servers.find(workspace.id(), serverId).map(server -> server.allows(tool)).orElse(false);
    }
}
