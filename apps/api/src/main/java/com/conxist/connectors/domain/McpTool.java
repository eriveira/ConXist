package com.conxist.connectors.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A tool an MCP server lists, as ConXist keeps it. Its name, title, description and schema come from the server and
 * are untrusted text: shown to the person and sent to a model as the tool's description, never followed.
 *
 * @param readOnly true only when the server marks the tool {@code readOnlyHint}; an unmarked tool may change things
 * @param allowed  whether the workspace lets agents use it. A tool that may change things starts off, and is allowed
 *                 only after the person confirmed a warning that agents can then use it without asking (owner,
 *                 2026-11-27)
 */
public record McpTool(String name, String title, String description, boolean readOnly,
        Map<String, Object> inputSchema, boolean allowed) {

    /** MCP's own rule for tool names. */
    public static final String NAME_PATTERN = "[A-Za-z0-9_\\-./]{1,128}";
    public static final Pattern NAME = Pattern.compile(NAME_PATTERN);
    public static final int MAX_TITLE_LENGTH = 200;
    public static final int MAX_DESCRIPTION_LENGTH = 2_000;

    public McpTool {
        if (name == null || !NAME.matcher(name).matches()) {
            throw new InvalidMcp("tool-name", "This server lists a tool with a name ConXist can't use.");
        }
        title = cut(title, MAX_TITLE_LENGTH);
        description = cut(description, MAX_DESCRIPTION_LENGTH);
        inputSchema = Collections.unmodifiableMap(new LinkedHashMap<>(
                Objects.requireNonNullElse(inputSchema, Map.of("type", "object"))));
    }

    public McpTool withAllowed(boolean value) {
        return new McpTool(name, title, description, readOnly, inputSchema, value);
    }

    /** Whether a name is one MCP allows; tools with other names are left out of a list. */
    public static boolean usableName(String name) {
        return name != null && NAME.matcher(name).matches();
    }

    private static String cut(String value, int max) {
        String text = value == null ? "" : value.strip();
        return text.codePointCount(0, text.length()) <= max ? text
                : text.substring(0, text.offsetByCodePoints(0, max - 1)) + "…";
    }
}
