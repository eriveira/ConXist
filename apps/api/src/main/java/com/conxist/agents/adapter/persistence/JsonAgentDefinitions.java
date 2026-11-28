package com.conxist.agents.adapter.persistence;

import java.util.List;
import java.util.UUID;
import com.conxist.agents.domain.AgentDefinition;
import com.conxist.agents.domain.AgentModel;
import com.conxist.agents.domain.AgentTool;
import com.conxist.agents.domain.McpToolPick;
import com.conxist.agents.domain.SkillPin;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Stores an agent definition as JSON. The stored shape is its own record, versioned by {@code v}, so the domain can
 * change without breaking definitions already saved. Shape 2 (ADR 0023) dropped the knowledge sources, style, takes-in,
 * gives-back and limits of shape 1; a shape 1 row reads the same, and those fields are ignored.
 */
final class JsonAgentDefinitions {

    private static final int SHAPE = 2;
    private static final int FIRST_SHAPE = 1;

    private final JsonMapper json = JsonMapper.builder().build();
    /** Reads shape 1 rows, whose removed fields are skipped; a shape 2 row with a field it lacks is refused. */
    private final JsonMapper firstShape = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    String write(AgentDefinition definition) {
        AgentModel model = definition.model();
        return json.writeValueAsString(new Stored(SHAPE, definition.name(), definition.purpose(),
                definition.instructions(),
                definition.skills().stream().map(pin -> new StoredPin(pin.skillId(), pin.version())).toList(),
                definition.tools().stream().map(Enum::name).toList(),
                model == null ? null : model.provider(), model == null ? null : model.model(),
                model == null ? null : model.effort().name(),
                definition.mcpTools().stream().map(pick -> new StoredMcpTool(pick.serverId(), pick.tool())).toList()));
    }

    AgentDefinition read(String text) {
        JsonNode tree = json.readTree(text);
        int shape = tree.path("v").asInt();
        if (shape != SHAPE && shape != FIRST_SHAPE) {
            throw new IllegalStateException("Unknown agent definition shape " + shape);
        }
        Stored stored = (shape == FIRST_SHAPE ? firstShape : json).treeToValue(tree, Stored.class);
        return new AgentDefinition(stored.name(), stored.purpose(), stored.instructions(), pins(stored.skills()),
                stored.tools().stream().map(AgentTool::valueOf).toList(), model(stored), mcpTools(stored.mcpTools()));
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
    record Stored(int v, String name, String purpose, String instructions, List<StoredPin> skills, List<String> tools,
            String provider, String model, String effort, List<StoredMcpTool> mcpTools) {
    }

    record StoredMcpTool(UUID serverId, String tool) {
    }

    record StoredPin(UUID skillId, int version) {
    }
}
