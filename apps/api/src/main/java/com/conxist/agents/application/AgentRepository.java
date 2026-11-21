package com.conxist.agents.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.conxist.agents.domain.Agent;
import com.conxist.agents.domain.AgentVersion;

public interface AgentRepository {

    /** Saves a new agent unless its workspace has another agent with the name (ignoring case). */
    boolean insertIfNameFree(Agent agent);

    /** Saves the agent only if it is still at {@code expectedRevision}; false when someone changed it first. */
    boolean update(Agent agent, long expectedRevision);

    Optional<Agent> find(UUID workspaceId, UUID agentId);

    /** Every agent of the workspace, the most recently changed first. */
    List<Agent> findAll(UUID workspaceId);

    /** Whether another agent of the workspace than {@code except} has the name, ignoring case. */
    boolean nameTaken(UUID workspaceId, String name, UUID except);

    void addVersion(AgentVersion version);

    /** Every version of the agent, the newest first. */
    List<AgentVersion> versions(UUID agentId);

    Optional<AgentVersion> version(UUID agentId, int number);
}
