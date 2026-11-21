package com.conxist.agents.application;

import java.util.List;
import java.util.UUID;
import com.conxist.agents.domain.Agent;
import com.conxist.platform.Conflict;

/** Deletes an agent and all its versions for good, unless something still uses it. */
public final class DeleteAgent {

    private final AgentBook book;
    private final AgentUses uses;

    public DeleteAgent(AgentBook book, AgentUses uses) {
        this.book = book;
        this.uses = uses;
    }

    /** What uses the agent; while this is not empty, it can't be deleted. */
    public List<AgentUse> uses(UUID agentId) {
        Agent agent = book.load(agentId);
        return uses.of(book.workspaceId(), agent.id());
    }

    public void delete(UUID agentId, long expectedRevision) {
        book.inTransaction(() -> {
            Agent agent = book.load(agentId, expectedRevision);
            if (!uses.of(book.workspaceId(), agent.id()).isEmpty()) {
                throw new Conflict("agent.in-use", "Something still uses this agent. Remove it from there first.");
            }
            if (!book.agents().delete(book.workspaceId(), agent.id(), expectedRevision)) {
                throw new Conflict("agent.changed", "This agent was changed in another tab. Reload to see the latest.");
            }
            return null;
        });
    }
}
