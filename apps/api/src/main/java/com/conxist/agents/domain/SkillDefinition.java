package com.conxist.agents.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A skill: a way of working written once (steps, rules, examples) that any agent can follow. It only adds
 * instructions; it never gives an agent access or tools. {@code needs} names the tools it expects the agent to have.
 * The limits follow the SKILL.md format, so a skill written for another tool can be imported as it is.
 */
public record SkillDefinition(String name, String description, String instructions, List<AgentTool> needs) {

    public static final int MAX_NAME_LENGTH = 64;
    public static final int MAX_DESCRIPTION_LENGTH = 1_024;
    public static final int MAX_INSTRUCTIONS_LENGTH = 20_000;

    /** Checks the lengths every draft must keep, and trims and de-duplicates what the person typed. */
    public SkillDefinition {
        String trimmedName = stripped(name);
        if (trimmedName.isEmpty()) {
            throw new InvalidSkill("name-required", "Give your skill a name.");
        }
        name = limited(trimmedName, MAX_NAME_LENGTH, "name-too-long",
                "Keep the name to " + MAX_NAME_LENGTH + " characters.");
        description = limited(stripped(description), MAX_DESCRIPTION_LENGTH, "description-too-long",
                "Keep \"When to use it\" to " + MAX_DESCRIPTION_LENGTH + " characters.");
        instructions = limited(Optional.ofNullable(instructions).map(String::stripTrailing).orElse(""),
                MAX_INSTRUCTIONS_LENGTH, "instructions-too-long",
                "Keep the instructions to " + MAX_INSTRUCTIONS_LENGTH + " characters.");
        needs = distinct(needs);
    }

    /** What still stops this draft from being published. */
    public List<String> problems() {
        List<String> problems = new ArrayList<>();
        if (description.isBlank()) {
            problems.add("description-required");
        }
        if (instructions.isBlank()) {
            problems.add("instructions-required");
        }
        return List.copyOf(problems);
    }

    private static String limited(String value, int max, String code, String message) {
        if (value.codePointCount(0, value.length()) > max) {
            throw new InvalidSkill(code, message);
        }
        return value;
    }

    private static String stripped(String value) {
        return Optional.ofNullable(value).map(String::strip).orElse("");
    }

    private static List<AgentTool> distinct(List<AgentTool> values) {
        if (values == null) {
            return List.of();
        }
        if (values.stream().anyMatch(Objects::isNull)) {
            throw new InvalidSkill("invalid", "Some fields are missing or not valid.");
        }
        return List.copyOf(new LinkedHashSet<>(values));
    }
}
