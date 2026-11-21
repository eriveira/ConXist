package com.conxist.agents.domain;

/** Where a run stops, whichever comes first. */
public record Limits(int maxSteps, int timeoutSeconds, int maxToolCalls) {

    public static final Limits DEFAULT = new Limits(6, 120, 10);
    public static final int MAX_STEPS = 20;
    public static final int MIN_TIMEOUT_SECONDS = 10;
    public static final int MAX_TIMEOUT_SECONDS = 600;
    public static final int MAX_TOOL_CALLS = 50;

    public Limits {
        if (maxSteps < 1 || maxSteps > MAX_STEPS) {
            throw new InvalidAgent("limits-steps", "Steps must be between 1 and " + MAX_STEPS + ".");
        }
        if (timeoutSeconds < MIN_TIMEOUT_SECONDS || timeoutSeconds > MAX_TIMEOUT_SECONDS) {
            throw new InvalidAgent("limits-time",
                    "Time must be between " + MIN_TIMEOUT_SECONDS + " and " + MAX_TIMEOUT_SECONDS + " seconds.");
        }
        if (maxToolCalls < 0 || maxToolCalls > MAX_TOOL_CALLS) {
            throw new InvalidAgent("limits-tool-calls", "Tool calls must be between 0 and " + MAX_TOOL_CALLS + ".");
        }
    }
}
