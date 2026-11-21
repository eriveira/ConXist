package com.conxist.agents.application;

import java.util.UUID;
import com.conxist.agents.domain.Agent;
import com.conxist.agents.domain.AgentVersion;

/** Copies a published version into the draft, so the person can go back to it and publish it again. */
public final class RestoreAgentVersion {

    private final AgentBook book;

    public RestoreAgentVersion(AgentBook book) {
        this.book = book;
    }

    public AgentView restore(UUID agentId, int number, long expectedRevision) {
        return book.inTransaction(() -> {
            Agent agent = book.load(agentId, expectedRevision);
            AgentVersion version = book.version(agent, number);
            Agent restored = agent.restore(version, book.clock().instant());
            book.save(agent, restored);
            return book.view(restored);
        });
    }
}
