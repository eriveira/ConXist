package com.conxist.agents.domain;

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

    /** False for a tool that changes something; such a tool must wait for a person's approval when it runs. */
    public boolean readOnly() {
        return readOnly;
    }
}
