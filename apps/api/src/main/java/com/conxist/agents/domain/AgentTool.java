package com.conxist.agents.domain;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The tools an agent may be allowed. All of them only read; a tool that changes something will always wait for a
 * person's approval (TOOL_CALLING_STANDARDS.md §3) and is added with the runtime that enforces it.
 */
public enum AgentTool {
    LIST_SOURCES("conxist.list_sources", true),
    LIST_MEETINGS("conxist.list_meetings", true),
    GET_TRANSCRIPT("conxist.get_transcript", true);

    private final String toolName;
    private final boolean readOnly;

    AgentTool(String toolName, boolean readOnly) {
        this.toolName = toolName;
        this.readOnly = readOnly;
    }

    /** The name the model sees, namespaced by who provides the tool. */
    public String toolName() {
        return toolName;
    }

    /**
     * The tools a text names, by their full name or the name after the dot (for example {@code get_transcript}), in
     * the order of this list. A skill that says "read it with get_transcript" needs that tool.
     */
    public static List<AgentTool> mentionedIn(String text) {
        String value = text == null ? "" : text;
        return Arrays.stream(values()).filter(tool -> tool.mention().matcher(value).find()).toList();
    }

    private Pattern mention() {
        String shortName = toolName.substring(toolName.indexOf('.') + 1);
        return Pattern.compile("(?<![A-Za-z0-9_.])(conxist\\.)?" + Pattern.quote(shortName) + "(?![A-Za-z0-9_])");
    }

    /** False for a tool that changes something; such a tool must wait for a person's approval when it runs. */
    public boolean readOnly() {
        return readOnly;
    }
}
