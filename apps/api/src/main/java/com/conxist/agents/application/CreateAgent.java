package com.conxist.agents.application;

import java.util.List;
import java.util.UUID;
import com.conxist.agents.AgentModels;
import com.conxist.agents.domain.Agent;
import com.conxist.agents.domain.AgentDefinition;
import com.conxist.agents.domain.AgentModel;
import com.conxist.agents.domain.InputKind;
import com.conxist.agents.domain.Limits;
import com.conxist.agents.domain.OutputKind;
import com.conxist.agents.domain.Style;

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
        AgentDefinition draft = new AgentDefinition(name, "", "", List.of(), List.of(), List.of(), model,
                Style.PRECISE, List.of(InputKind.TRANSCRIPT), OutputKind.FREE_TEXT, Limits.DEFAULT);
        Agent agent = Agent.create(UUID.randomUUID(), book.workspaceId(), draft, book.clock().instant());
        return book.inTransaction(() -> {
            String chosen = agent.draft().name();
            if (!book.agents().insertIfNameFree(agent)) {
                throw AgentBook.nameTaken(chosen);
            }
            return book.view(agent);
        });
    }
}
