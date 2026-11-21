package com.conxist.agents.adapter.persistence;

import java.util.List;
import java.util.UUID;
import com.conxist.agents.domain.AgentDefinition;
import com.conxist.agents.domain.AgentTool;
import com.conxist.agents.domain.InputKind;
import com.conxist.agents.domain.Limits;
import com.conxist.agents.domain.ModelPolicy;
import com.conxist.agents.domain.OutputKind;
import com.conxist.agents.domain.SkillPin;
import com.conxist.agents.domain.Style;
import tools.jackson.databind.json.JsonMapper;

/**
 * Stores an agent definition as JSON. The stored shape is its own record, versioned by {@code v}, so the domain can
 * change without breaking definitions already saved; a new shape adds a reader for the old one.
 */
final class JsonAgentDefinitions {

    private static final int SHAPE = 1;

    private final JsonMapper json = JsonMapper.builder().build();

    String write(AgentDefinition definition) {
        return json.writeValueAsString(new Stored(SHAPE, definition.name(), definition.purpose(),
                definition.instructions(), definition.knowledgeSources(),
                definition.skills().stream().map(pin -> new StoredPin(pin.skillId(), pin.version())).toList(),
                definition.tools().stream().map(Enum::name).toList(), definition.model().name(),
                definition.style().name(), definition.accepts().stream().map(Enum::name).toList(),
                definition.givesBack().name(), new StoredLimits(definition.limits().maxSteps(),
                        definition.limits().timeoutSeconds(), definition.limits().maxToolCalls())));
    }

    AgentDefinition read(String text) {
        Stored stored = json.readValue(text, Stored.class);
        if (stored.v() != SHAPE) {
            throw new IllegalStateException("Unknown agent definition shape " + stored.v());
        }
        return new AgentDefinition(stored.name(), stored.purpose(), stored.instructions(),
                stored.knowledgeSources(), pins(stored.skills()),
                stored.tools().stream().map(AgentTool::valueOf).toList(),
                ModelPolicy.valueOf(stored.model()), Style.valueOf(stored.style()),
                stored.accepts().stream().map(InputKind::valueOf).toList(), OutputKind.valueOf(stored.givesBack()),
                new Limits(stored.limits().maxSteps(), stored.limits().timeoutSeconds(),
                        stored.limits().maxToolCalls()));
    }

    /** Definitions saved before skills existed have none. */
    private static List<SkillPin> pins(List<StoredPin> stored) {
        return stored == null ? List.of()
                : stored.stream().map(pin -> new SkillPin(pin.skillId(), pin.version())).toList();
    }

    /** @param skills added after shape 1 was first written; absent in older rows, which read as no skills */
    record Stored(int v, String name, String purpose, String instructions, List<UUID> knowledgeSources,
            List<StoredPin> skills, List<String> tools, String model, String style, List<String> accepts, String givesBack,
            StoredLimits limits) {
    }

    record StoredPin(UUID skillId, int version) {
    }

    record StoredLimits(int maxSteps, int timeoutSeconds, int maxToolCalls) {
    }
}
