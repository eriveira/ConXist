package com.conxist.agents;

import java.util.List;
import java.util.UUID;

/**
 * An agent as a run reads it: its job and the skills it follows with their text at the pinned version. Choices are the
 * names of the agent's own settings ({@code provider} and {@code model} as the API's settings list them, {@code effort}
 * LOW, MEDIUM or HIGH, or all three null when no model is picked), so a reader does not depend on this module's inner
 * types. Where a run stops is the workspace's run limits, not the agent's (ADR 0023).
 *
 * @param tools    the tool names the agent may use, such as {@code conxist.get_transcript}
 * @param skills   the skills the agent follows that this workspace can use, in the agent's order
 * @param problems what stops the draft from being published; a run refuses a draft that has any
 * @param archived  whether the agent is archived; a run refuses it
 * @param mcpTools  the tools of the workspace's MCP servers the agent may use (ADR 0017); a run offers only those the
 *                  workspace still allows
 */
public record AgentBrief(
        UUID id,
        String name,
        String purpose,
        String instructions,
        List<String> tools,
        String provider,
        String model,
        String effort,
        List<Skill> skills,
        List<String> problems,
        boolean archived,
        List<McpTool> mcpTools) {

    public AgentBrief {
        mcpTools = List.copyOf(mcpTools);
        tools = List.copyOf(tools);
        skills = List.copyOf(skills);
        problems = List.copyOf(problems);
    }

    /** A brief without MCP tools. */
    public AgentBrief(UUID id, String name, String purpose, String instructions, List<String> tools, String provider,
            String model, String effort, List<Skill> skills, List<String> problems, boolean archived) {
        this(id, name, purpose, instructions, tools, provider, model, effort, skills, problems, archived, List.of());
    }

    /** A tool of an MCP server: the server's id and the tool's name as the server lists it. */
    public record McpTool(UUID serverId, String tool) {
    }

    /** @param source YOURS or IMPORTED */
    public record Skill(UUID id, int version, String name, String source, String instructions) {
    }
}
