package com.conxist.agents.application;

import java.util.UUID;
import com.conxist.agents.AgentModels;
import com.conxist.agents.domain.Agent;
import com.conxist.agents.domain.AgentModel;
import com.conxist.agents.domain.AgentTemplate;
import com.conxist.agents.domain.Language;

/** Starts an agent from a template, as a draft that is not published yet, on the model the template starts on. */
public final class CreateAgent {

    private final AgentBook book;

    public CreateAgent(AgentBook book) {
        this.book = book;
    }

    /** @param language the person's language, for the template's brief */
    public AgentView create(AgentTemplate template, String name, Language language) {
        AgentModels.Start start = book.models().start(template.start().map(Enum::name));
        AgentModel model = new AgentModel(start.provider(), start.model(), AgentModel.Effort.valueOf(start.effort()));
        Agent agent = Agent.create(UUID.randomUUID(), book.workspaceId(), template.draft(name, language, model),
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
