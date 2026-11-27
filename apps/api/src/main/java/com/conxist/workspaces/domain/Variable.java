package com.conxist.workspaces.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import com.conxist.platform.RuleViolation;
import com.conxist.platform.WorkspaceVariables;

/**
 * A name and a value the workspace's agents and skills share, such as {@code company_name} or {@code topics}
 * (ADR 0018). Instructions use one by writing {@code {{name}}}; a run puts the value in its place.
 */
public record Variable(UUID id, UUID workspaceId, String name, String value, Instant createdAt) {

    public static final int MAX_NAME_LENGTH = 60;
    public static final int MAX_VALUE_LENGTH = 2_000;
    public static final int MAX_PER_WORKSPACE = 100;
    private static final Pattern NAME = Pattern.compile(WorkspaceVariables.NAME_PATTERN);

    /** Builds a variable, enforcing the rules every variable must meet. Lengths count characters as seen. */
    public static Variable create(UUID id, UUID workspaceId, String name, String value, Instant createdAt) {
        return new Variable(id, workspaceId, requireName(name), requireValue(value), createdAt);
    }

    public Variable withText(String name, String value) {
        return create(id, workspaceId, name, value, createdAt);
    }

    private static String requireName(String name) {
        String trimmed = stripped(name);
        if (trimmed.isEmpty()) {
            throw invalid("name-required", "Give the variable a name.");
        }
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw invalid("name-too-long", "Keep the name to " + MAX_NAME_LENGTH + " characters.");
        }
        if (!NAME.matcher(trimmed).matches()) {
            throw invalid("name-invalid", "Use letters, digits and underscores, starting with a letter.");
        }
        return trimmed;
    }

    private static String requireValue(String value) {
        String trimmed = stripped(value);
        if (trimmed.isEmpty()) {
            throw invalid("value-required", "Give the variable a value.");
        }
        if (trimmed.codePointCount(0, trimmed.length()) > MAX_VALUE_LENGTH) {
            throw invalid("value-too-long", "Keep the value to " + MAX_VALUE_LENGTH + " characters.");
        }
        return trimmed;
    }

    static RuleViolation invalid(String code, String userMessage) {
        return new RuleViolation("variable." + code, userMessage);
    }

    private static String stripped(String value) {
        return Optional.ofNullable(value).map(String::strip).orElse("");
    }
}
