package com.conxist.agents;

import java.util.List;
import java.util.UUID;

/**
 * An agent as a run reads it: its job, the skills it follows with their text at the pinned version, and its limits.
 * Choices are the names of the agent's own settings ({@code provider} and {@code model} as the API's settings list
 * them, {@code effort} LOW, MEDIUM or HIGH, or all three null when no model is picked; {@code style} PRECISE
 * or CREATIVE; {@code givesBack} an output kind such as REQUIREMENTS), so a reader does not depend on this module's
 * inner types.
 *
 * @param tools    the tool names the agent may use, such as {@code conxist.get_transcript}
 * @param skills   the skills the agent follows that this workspace can use, in the agent's order
 * @param problems what stops the draft from being published; a run refuses a draft that has any
 * @param archived  whether the agent is archived; a run refuses it
 */
public record AgentBrief(
        UUID id,
        String name,
        String purpose,
        String instructions,
        List<UUID> knowledgeSources,
        List<String> tools,
        String provider,
        String model,
        String effort,
        String style,
        String givesBack,
        int maxSteps,
        int timeoutSeconds,
        int maxToolCalls,
        List<Skill> skills,
        List<String> problems,
        boolean archived) {

    public AgentBrief {
        knowledgeSources = List.copyOf(knowledgeSources);
        tools = List.copyOf(tools);
        skills = List.copyOf(skills);
        problems = List.copyOf(problems);
    }

    /** @param source YOURS or IMPORTED */
    public record Skill(UUID id, int version, String name, String source, String instructions) {
    }
}
