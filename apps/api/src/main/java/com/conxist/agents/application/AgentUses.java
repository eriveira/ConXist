package com.conxist.agents.application;

import java.util.List;
import java.util.UUID;

/**
 * What uses an agent of the workspace. Nothing does yet: flows and schedules will (ADR 0010, step 4) and add their
 * uses here, so deleting an agent they run is refused without changing the delete itself.
 */
@FunctionalInterface
public interface AgentUses {

    List<AgentUse> of(UUID workspaceId, UUID agentId);

    /** Until flows and schedules exist, no agent is used by anything. */
    static AgentUses none() {
        return (workspaceId, agentId) -> List.of();
    }
}
