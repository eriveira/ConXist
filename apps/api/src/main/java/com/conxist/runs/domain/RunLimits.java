package com.conxist.runs.domain;

import com.conxist.platform.RuleViolation;

/**
 * Where every agent run of a workspace stops, whichever comes first: model turns, seconds and tool calls (ADR 0023).
 * The run loop keeps them, not the prompt.
 */
public record RunLimits(int maxSteps, int timeoutSeconds, int maxToolCalls) {

    /** What a workspace that never changed its limits uses. */
    public static final RunLimits DEFAULT = new RunLimits(6, 120, 10);
    public static final int MAX_STEPS = 20;
    public static final int MIN_TIMEOUT_SECONDS = 10;
    public static final int MAX_TIMEOUT_SECONDS = 600;
    public static final int MAX_TOOL_CALLS = 50;

    public RunLimits {
        if (maxSteps < 1 || maxSteps > MAX_STEPS) {
            throw new RuleViolation("run-limits.steps", "Steps must be between 1 and " + MAX_STEPS + ".");
        }
        if (timeoutSeconds < MIN_TIMEOUT_SECONDS || timeoutSeconds > MAX_TIMEOUT_SECONDS) {
            throw new RuleViolation("run-limits.time",
                    "Time must be between " + MIN_TIMEOUT_SECONDS + " and " + MAX_TIMEOUT_SECONDS + " seconds.");
        }
        if (maxToolCalls < 0 || maxToolCalls > MAX_TOOL_CALLS) {
            throw new RuleViolation("run-limits.tool-calls",
                    "Tool calls must be between 0 and " + MAX_TOOL_CALLS + ".");
        }
    }
}
