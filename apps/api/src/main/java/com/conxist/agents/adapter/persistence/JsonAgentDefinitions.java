package com.conxist.agents.adapter.persistence;

import java.util.List;
import java.util.UUID;
import com.conxist.agents.domain.AgentDefinition;
import com.conxist.agents.domain.AgentModel;
import com.conxist.agents.domain.AgentTool;
import com.conxist.agents.domain.InputKind;
import com.conxist.agents.domain.Limits;
import com.conxist.agents.domain.McpToolPick;
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
        AgentModel model = definition.model();
        return json.writeValueAsString(new Stored(SHAPE, definition.name(), definition.purpose(),
                definition.instructions(), definition.knowledgeSources(),
                definition.skills().stream().map(pin -> new StoredPin(pin.skillId(), pin.version())).toList(),
                definition.tools().stream().map(Enum::name).toList(),
                model == null ? null : model.provider(), model == null ? null : model.model(),
                model == null ? null : model.effort().name(),
                definition.style().name(), definition.accepts().stream().map(Enum::name).toList(),
                definition.givesBack().name(), new StoredLimits(definition.limits().maxSteps(),
                        definition.limits().timeoutSeconds(), definition.limits().maxToolCalls()),
                definition.mcpTools().stream().map(pick -> new StoredMcpTool(pick.serverId(), pick.tool())).toList()));
    }

    AgentDefinition read(String text) {
        Stored stored = json.readValue(text, Stored.class);
        if (stored.v() != SHAPE) {
            throw new IllegalStateException("Unknown agent definition shape " + stored.v());
        }
        return new AgentDefinition(stored.name(), stored.purpose(), stored.instructions(),
                stored.knowledgeSources(), pins(stored.skills()),
                stored.tools().stream().map(AgentTool::valueOf).toList(),
                model(stored), Style.valueOf(stored.style()),
                stored.accepts().stream().map(InputKind::valueOf).toList(), OutputKind.valueOf(stored.givesBack()),
                new Limits(stored.limits().maxSteps(), stored.limits().timeoutSeconds(),
                        stored.limits().maxToolCalls()), mcpTools(stored.mcpTools()));
    }

    /** Definitions saved before ADR 0017 use no MCP tools. */
    private static List<McpToolPick> mcpTools(List<StoredMcpTool> stored) {
        return stored == null ? List.of()
                : stored.stream().map(tool -> new McpToolPick(tool.serverId(), tool.tool())).toList();
    }

    /** No model is picked when the provider is missing; V14 gave every row saved before ADR 0014 a provider. */
    private static AgentModel model(Stored stored) {
        return stored.provider() == null || stored.model() == null || stored.effort() == null ? null
                : new AgentModel(stored.provider(), stored.model(), AgentModel.Effort.valueOf(stored.effort()));
    }

    /** Definitions saved before skills existed have none. */
    private static List<SkillPin> pins(List<StoredPin> stored) {
        return stored == null ? List.of()
                : stored.stream().map(pin -> new SkillPin(pin.skillId(), pin.version())).toList();
    }

    /**
     * @param skills   added after shape 1 was first written; absent in older rows, which read as no skills
     * @param provider added with ADR 0014, with {@code effort}; {@code model} was FAST, BALANCED or THOROUGH before
     * @param mcpTools added with ADR 0017; absent in older rows, which read as none
     */
    record Stored(int v, String name, String purpose, String instructions, List<UUID> knowledgeSources,
            List<StoredPin> skills, List<String> tools, String provider, String model, String effort, String style,
            List<String> accepts, String givesBack, StoredLimits limits, List<StoredMcpTool> mcpTools) {
    }

    record StoredMcpTool(UUID serverId, String tool) {
    }

    record StoredPin(UUID skillId, int version) {
    }

    record StoredLimits(int maxSteps, int timeoutSeconds, int maxToolCalls) {
    }
}
