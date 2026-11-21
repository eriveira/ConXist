package com.conxist.agents.application;

import java.util.List;
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
            List<String> problems = book.problems(agent.draft());
            if (problems.contains(AgentBook.SOURCE_MISSING)) {
                throw new InvalidAgent(AgentBook.SOURCE_MISSING,
                        "A source this agent reads was removed. Remove it from Knowledge, then publish.");
            }
            if (problems.contains(AgentBook.SKILL_MISSING)) {
                throw new InvalidAgent(AgentBook.SKILL_MISSING,
                        "A skill this agent follows was removed. Remove it from Skills, then publish.");
            }
            if (problems.contains(AgentBook.SKILL_NEEDS_TOOL)) {
                throw new InvalidAgent(AgentBook.SKILL_NEEDS_TOOL,
                        "A skill this agent follows needs a tool that is off. Turn it on in Tools, or remove the skill.");
            }
            Agent.Published published = agent.publish(book.clock().instant());
            book.save(agent, published.agent());
            book.agents().addVersion(published.version());
            return book.view(published.agent());
        });
    }
}
