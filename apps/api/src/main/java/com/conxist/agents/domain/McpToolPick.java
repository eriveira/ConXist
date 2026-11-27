package com.conxist.agents.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * A tool of an MCP server the workspace added (ADR 0017), picked for an agent: the server's id and the tool's name as
 * the server lists it. Whether the workspace still allows it is checked when the draft is read, not here.
 */
public record McpToolPick(UUID serverId, String tool) {

    /** MCP tool names are 1 to 128 characters of letters, digits, {@code _ - . /}. */
    public static final int MAX_TOOL_LENGTH = 128;

    public McpToolPick {
        Objects.requireNonNull(serverId, "serverId");
        if (tool == null || tool.isEmpty() || tool.length() > MAX_TOOL_LENGTH
                || !tool.chars().allMatch(McpToolPick::allowed)) {
            throw new InvalidAgent("invalid", "Some fields are missing or not valid.");
        }
    }

    private static boolean allowed(int c) {
        return c < 128 && (Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.' || c == '/');
    }
}
