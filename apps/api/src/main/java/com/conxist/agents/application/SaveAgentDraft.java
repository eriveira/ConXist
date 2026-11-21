package com.conxist.agents.application;

import java.util.UUID;
import com.conxist.agents.domain.Agent;
import com.conxist.agents.domain.AgentDefinition;

/** Saves the draft as the person edits it. What is published does not change until they publish. */
public final class SaveAgentDraft {

    private final AgentBook book;

    public SaveAgentDraft(AgentBook book) {
        this.book = book;
    }

    public AgentView save(UUID agentId, long expectedRevision, AgentDefinition draft) {
        return book.inTransaction(() -> {
            Agent agent = book.load(agentId, expectedRevision);
            Agent changed = agent.withDraft(draft, book.clock().instant());
            book.save(agent, changed);
            return book.view(changed);
        });
    }
}
