package com.conxist.workspaces.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** A space for one team or product, with its own sources. */
public record Workspace(UUID id, String name, String description, Instant createdAt) {

    public static final int MAX_NAME_LENGTH = 80;
    public static final int MAX_DESCRIPTION_LENGTH = 500;

    /** Builds a new workspace, enforcing the rules every workspace must meet. Lengths count characters as seen. */
    public static Workspace create(UUID id, String name, String description, Instant now) {
        return new Workspace(id, requireName(name), requireDescription(description), now);
    }

    private static String requireName(String name) {
        String trimmed = stripped(name);
        if (trimmed.isEmpty()) {
            throw new InvalidWorkspace("name-required", "Give your workspace a name to continue.");
        }
        if (length(trimmed) > MAX_NAME_LENGTH) {
            throw new InvalidWorkspace("name-too-long", "Keep the name to " + MAX_NAME_LENGTH + " characters.");
        }
        return trimmed;
    }

    private static String requireDescription(String description) {
        String trimmed = stripped(description);
        if (length(trimmed) > MAX_DESCRIPTION_LENGTH) {
            throw new InvalidWorkspace("description-too-long",
                    "Keep the description to " + MAX_DESCRIPTION_LENGTH + " characters.");
        }
        return trimmed;
    }

    private static String stripped(String value) {
        return Optional.ofNullable(value).map(String::strip).orElse("");
    }

    private static int length(String value) {
        return value.codePointCount(0, value.length());
    }
}
