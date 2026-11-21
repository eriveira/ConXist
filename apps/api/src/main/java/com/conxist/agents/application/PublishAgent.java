package com.conxist.agents.application;

import java.util.UUID;
import com.conxist.agents.domain.Agent;
import com.conxist.agents.domain.InvalidAgent;

/** Publishes the draft as the next numbered version, which never changes afterwards. */
public final class PublishAgent {

    private final AgentBook book;

    public PublishAgent(AgentBook book) {
        this.book = book;
    }

    public AgentView publish(UUID agentId, long expectedRevision) {
        return book.inTransaction(() -> {
            Agent agent = book.load(agentId, expectedRevision);
            if (book.problems(agent.draft()).contains(AgentBook.SOURCE_MISSING)) {
                throw new InvalidAgent(AgentBook.SOURCE_MISSING,
                        "A source this agent reads was removed. Remove it from Knowledge, then publish.");
            }
            Agent.Published published = agent.publish(book.clock().instant());
            book.save(agent, published.agent());
            book.agents().addVersion(published.version());
            return book.view(published.agent());
        });
    }
}
