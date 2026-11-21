package com.conxist.agents;

import java.util.Optional;
import java.util.UUID;

/** Lets another module read an agent of the current workspace as a run needs it. */
public interface AgentBriefs {

    /** The agent's current draft; empty when it is not an agent of the current workspace. */
    Optional<AgentBrief> draft(UUID agentId);
}
