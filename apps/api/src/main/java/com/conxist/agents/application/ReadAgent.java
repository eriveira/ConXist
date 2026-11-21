package com.conxist.agents.application;

import java.util.UUID;
import com.conxist.agents.domain.AgentVersion;
import com.conxist.platform.NotFound;

/** One agent of the current workspace, or one of its published versions. */
public final class ReadAgent {

    private final AgentBook book;

    public ReadAgent(AgentBook book) {
        this.book = book;
    }

    public AgentView get(UUID agentId) {
        return book.view(book.load(agentId));
    }

    public AgentVersion version(UUID agentId, int number) {
        book.load(agentId);
        return book.agents().version(agentId, number)
                .orElseThrow(() -> new NotFound("agent.version-not-found", "This version does not exist."));
    }
}
