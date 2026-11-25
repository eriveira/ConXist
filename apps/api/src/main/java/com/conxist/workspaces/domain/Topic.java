package com.conxist.workspaces.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import com.conxist.platform.RuleViolation;

/**
 * A subject the team talks about, such as "Checkout" or "Hiring", that the topic classifier sorts meetings into
 * (ADR 0014). The description says what belongs in it, so the classifier and a new teammate read it the same way.
 */
public record Topic(UUID id, UUID workspaceId, String name, String description, Instant createdAt) {

    public static final int MAX_NAME_LENGTH = 60;
    public static final int MAX_DESCRIPTION_LENGTH = 300;
    /** Enough for a team's subjects; more is a sign the list holds sub-topics. */
    public static final int MAX_PER_WORKSPACE = 50;

    /** Builds a topic, enforcing the rules every topic must meet. Lengths count characters as seen. */
    public static Topic create(UUID id, UUID workspaceId, String name, String description, Instant createdAt) {
        return new Topic(id, workspaceId, requireName(name), requireDescription(description), createdAt);
    }

    public Topic withText(String name, String description) {
        return create(id, workspaceId, name, description, createdAt);
    }

    private static String requireName(String name) {
        String trimmed = stripped(name);
        if (trimmed.isEmpty()) {
            throw invalid("name-required", "Give the topic a name.");
        }
        if (length(trimmed) > MAX_NAME_LENGTH) {
            throw invalid("name-too-long", "Keep the name to " + MAX_NAME_LENGTH + " characters.");
        }
        return trimmed;
    }

    private static String requireDescription(String description) {
        String trimmed = stripped(description);
        if (length(trimmed) > MAX_DESCRIPTION_LENGTH) {
            throw invalid("description-too-long",
                    "Keep the description to " + MAX_DESCRIPTION_LENGTH + " characters.");
        }
        return trimmed;
    }

    static RuleViolation invalid(String code, String userMessage) {
        return new RuleViolation("topic." + code, userMessage);
    }

    private static String stripped(String value) {
        return Optional.ofNullable(value).map(String::strip).orElse("");
    }

    private static int length(String value) {
        return value.codePointCount(0, value.length());
    }
}
