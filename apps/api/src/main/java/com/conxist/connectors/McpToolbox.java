package com.conxist.connectors;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The tools of the current workspace's MCP servers, as a run offers and calls them (ADR 0017). Only tools the
 * workspace allows and that only read are offered or called; everything a tool answers is untrusted data.
 */
public interface McpToolbox {

    /**
     * The most characters of input, as {@code String.valueOf(arguments)} measures them, that one call may send. A lookup
     * needs a few words; a limit keeps meeting text or earlier answers from leaving ConXist in a tool's arguments.
     */
    int MAX_ARGUMENT_CHARACTERS = 4_000;

    /**
     * The picked tools the workspace still allows, in the order picked, each with the name the model sees: letters,
     * digits, {@code _} and {@code -}, at most 64 characters, unique in the list. Picks it no longer allows are left out.
     */
    List<OfferedTool> offer(List<ToolRef> picks);

    /** Calls the tool on its server. A failure comes back as an error result the model can act on, never an exception. */
    Result call(ToolRef tool, Map<String, Object> arguments);

    record ToolRef(UUID serverId, String tool) {
    }

    /**
     * @param modelName   the name the model sees
     * @param description the server's description of the tool: untrusted text
     * @param inputSchema the JSON schema of the tool's input, as the server gives it
     */
    /** @param changesThings false only when the server marks the tool read-only; ConXist's integrations only read */
    record OfferedTool(ToolRef ref, String modelName, String description, Map<String, Object> inputSchema,
            boolean changesThings) {
    }

    /** @param text what the tool answered, or why it failed when {@code error} is true */
    record Result(String text, boolean error) {
    }
}
