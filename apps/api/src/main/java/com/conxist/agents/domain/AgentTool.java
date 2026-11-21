package com.conxist.agents.domain;

/**
 * The tools an agent may be allowed. All of them only read; a tool that changes something will always wait for a
 * person's approval (TOOL_CALLING_STANDARDS.md §3) and is added with the runtime that enforces it.
 */
public enum AgentTool {
    LIST_SOURCES("conxist.list_sources"),
    LIST_MEETINGS("conxist.list_meetings"),
    GET_TRANSCRIPT("conxist.get_transcript");

    private final String toolName;

    AgentTool(String toolName) {
        this.toolName = toolName;
    }

    /** The name the model sees, namespaced by who provides the tool. */
    public String toolName() {
        return toolName;
    }

    public boolean readOnly() {
        return true;
    }
}
