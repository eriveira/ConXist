package com.conxist.agents.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What an agent is: its purpose, its brief, the skills it follows, the tools it may use (its own and those of MCP
 * servers the workspace added) and how it thinks. Where a run stops is the workspace's run limits (ADR 0023). A draft
 * may be unfinished (even without a model); {@link #problems()} says what must be done before it is published.
 */
public record AgentDefinition(
        String name,
        String purpose,
        String instructions,
        List<SkillPin> skills,
        List<AgentTool> tools,
        AgentModel model,
        List<McpToolPick> mcpTools) {

    public static final int MAX_NAME_LENGTH = 60;
    public static final int MAX_PURPOSE_LENGTH = 200;
    public static final int MAX_INSTRUCTIONS_LENGTH = 8_000;
    public static final int MAX_SKILLS = 10;
    public static final int MAX_MCP_TOOLS = 30;

    /** Checks the lengths and sizes every draft must keep, and trims and de-duplicates what the person typed. */
    public AgentDefinition {
        name = requireName(name);
        purpose = limited(stripped(purpose), MAX_PURPOSE_LENGTH, "purpose-too-long",
                "Keep the purpose to " + MAX_PURPOSE_LENGTH + " characters.");
        instructions = limited(Optional.ofNullable(instructions).map(String::stripTrailing).orElse(""),
                MAX_INSTRUCTIONS_LENGTH, "instructions-too-long",
                "Keep the instructions to " + MAX_INSTRUCTIONS_LENGTH + " characters.");
        skills = distinct(skills);
        if (skills.size() > MAX_SKILLS) {
            throw new InvalidAgent("too-many-skills", "An agent can follow up to " + MAX_SKILLS + " skills.");
        }
        if (skills.stream().map(SkillPin::skillId).distinct().count() < skills.size()) {
            throw new InvalidAgent("skill-twice", "An agent follows one version of each skill.");
        }
        tools = distinct(tools);
        mcpTools = distinct(mcpTools);
        if (mcpTools.size() > MAX_MCP_TOOLS) {
            throw new InvalidAgent("too-many-mcp-tools", "An agent can use up to " + MAX_MCP_TOOLS + " MCP tools.");
        }
    }

    /** A new agent's first draft: only its name and model. It needs instructions before it can be published. */
    public static AgentDefinition empty(String name, AgentModel model) {
        return new AgentDefinition(name, "", "", List.of(), List.of(), model, List.of());
    }

    /** What still stops this draft from being published, in the order the page shows its sections. */
    public List<String> problems() {
        List<String> problems = new ArrayList<>();
        if (instructions.isBlank()) {
            problems.add("instructions-required");
        }
        if (model == null) {
            problems.add("model-required");
        }
        return List.copyOf(problems);
    }

    private static String requireName(String name) {
        String trimmed = stripped(name);
        if (trimmed.isEmpty()) {
            throw new InvalidAgent("name-required", "Give your agent a name.");
        }
        return limited(trimmed, MAX_NAME_LENGTH, "name-too-long",
                "Keep the name to " + MAX_NAME_LENGTH + " characters.");
    }

    private static String limited(String value, int max, String code, String message) {
        if (value.codePointCount(0, value.length()) > max) {
            throw new InvalidAgent(code, message);
        }
        return value;
    }

    private static String stripped(String value) {
        return Optional.ofNullable(value).map(String::strip).orElse("");
    }

    private static <T> List<T> distinct(List<T> values) {
        if (values == null) {
            return List.of();
        }
        if (values.stream().anyMatch(Objects::isNull)) {
            throw new InvalidAgent("invalid", "Some fields are missing or not valid.");
        }
        return List.copyOf(new LinkedHashSet<>(values));
    }
}
