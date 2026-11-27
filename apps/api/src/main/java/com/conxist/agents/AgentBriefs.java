package com.conxist.agents;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Lets another module read an agent of the current workspace as a run needs it. */
public interface AgentBriefs {

    /** The agent's current draft; empty when it is not an agent of the current workspace. */
    Optional<AgentBrief> draft(UUID agentId);

    /**
     * The names of the current workspace's agents whose draft runs on this model, archived ones left out, by name. Settings
     * keeps such a model allowed (ADR 0014).
     */
    List<String> usingModel(String provider, String model);

    /**
     * Every use of an MCP server's tool by the current workspace's agents, in a draft or a live version, archived agents
     * too: a server or tool in use can't be removed (ADR 0017).
     */
    List<McpToolUse> mcpToolUses();

    /**
     * @param where    DRAFT, LIVE or BOTH
     * @param archived whether the agent is archived
     */
    record McpToolUse(UUID serverId, String tool, UUID agentId, String agentName, String where, boolean archived) {
    }
}
