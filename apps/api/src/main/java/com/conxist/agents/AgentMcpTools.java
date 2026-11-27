package com.conxist.agents;

import java.util.UUID;

/**
 * Whether a tool of an MCP server the workspace added may still be used by agents, answered by the module that keeps
 * those servers (ADR 0017). A tool the workspace no longer allows shows as a problem on the agent's draft.
 */
public interface AgentMcpTools {

    /** Whether the current workspace has the server and allows agents to use the tool. */
    boolean allowed(UUID serverId, String tool);

    /** For a workspace that has no MCP servers. */
    static AgentMcpTools none() {
        return (serverId, tool) -> false;
    }
}
