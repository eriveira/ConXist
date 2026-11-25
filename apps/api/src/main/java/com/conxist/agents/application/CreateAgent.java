package com.conxist.agents.application;

import java.util.UUID;
import com.conxist.agents.AgentModels;
import com.conxist.agents.domain.Agent;
import com.conxist.agents.domain.AgentDefinition;
import com.conxist.agents.domain.AgentModel;

/**
 * Starts an empty agent with the name the person chose, as a draft that is not published yet, on the workspace's
 * default model. The person writes its purpose, instructions and tools.
 */
public final class CreateAgent {

    private final AgentBook book;

    public CreateAgent(AgentBook book) {
        this.book = book;
    }

    public AgentView create(String name) {
        AgentModels.Start start = book.models().start();
        AgentModel model = new AgentModel(start.provider(), start.model(), AgentModel.Effort.valueOf(start.effort()));
        Agent agent = Agent.create(UUID.randomUUID(), book.workspaceId(), AgentDefinition.empty(name, model),
                book.clock().instant());
        return book.inTransaction(() -> {
            String chosen = agent.draft().name();
            if (!book.agents().insertIfNameFree(agent)) {
                throw AgentBook.nameTaken(chosen);
            }
            return book.view(agent);
        });
    }
}
