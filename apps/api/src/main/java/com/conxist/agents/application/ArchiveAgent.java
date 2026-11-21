package com.conxist.agents.application;

import java.util.UUID;
import com.conxist.agents.domain.Agent;

/** Archives an agent the team no longer uses, or brings it back. Its versions are kept either way. */
public final class ArchiveAgent {

    private final AgentBook book;

    public ArchiveAgent(AgentBook book) {
        this.book = book;
    }

    public AgentView archive(UUID agentId, long expectedRevision) {
        return book.inTransaction(() -> {
            Agent agent = book.load(agentId, expectedRevision);
            Agent archived = agent.archive(book.clock().instant());
            book.save(agent, archived);
            return book.view(archived);
        });
    }

    public AgentView unarchive(UUID agentId, long expectedRevision) {
        return book.inTransaction(() -> {
            Agent agent = book.load(agentId, expectedRevision);
            Agent active = agent.unarchive(book.clock().instant());
            book.save(agent, active);
            return book.view(active);
        });
    }
}
