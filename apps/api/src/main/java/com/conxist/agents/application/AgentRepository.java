package com.conxist.agents.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.conxist.agents.domain.Agent;
import com.conxist.agents.application.AgentView.VersionInfo;
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

    /**
     * Deletes the workspace's agent and all its versions, only if it is still at {@code expectedRevision};
     * false when it changed first or is not there.
     */
    boolean delete(UUID workspaceId, UUID agentId, long expectedRevision);

    void addVersion(AgentVersion version);

    /** Number and date of every version of the workspace's agent, the newest first; definitions are not read. */
    List<VersionInfo> versionInfos(UUID workspaceId, UUID agentId);

    /** A version of the workspace's agent; empty when the agent belongs to another workspace. */
    Optional<AgentVersion> version(UUID workspaceId, UUID agentId, int number);
}
