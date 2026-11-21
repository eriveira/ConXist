package com.conxist.agents.adapter.persistence;

import java.util.List;
import com.conxist.agents.domain.AgentTool;
import com.conxist.agents.domain.SkillDefinition;
import tools.jackson.databind.json.JsonMapper;

/** Stores a skill definition as JSON, in its own shape versioned by {@code v} (see JsonAgentDefinitions). */
final class JsonSkillDefinitions {

    private static final int SHAPE = 1;

    private final JsonMapper json = JsonMapper.builder().build();

    String write(SkillDefinition definition) {
        return json.writeValueAsString(new Stored(SHAPE, definition.name(), definition.description(),
                definition.instructions(), definition.needs().stream().map(Enum::name).toList()));
    }

    SkillDefinition read(String text) {
        Stored stored = json.readValue(text, Stored.class);
        if (stored.v() != SHAPE) {
            throw new IllegalStateException("Unknown skill definition shape " + stored.v());
        }
        return new SkillDefinition(stored.name(), stored.description(), stored.instructions(),
                stored.needs().stream().map(AgentTool::valueOf).toList());
    }

    record Stored(int v, String name, String description, String instructions, List<String> needs) {
    }
}
