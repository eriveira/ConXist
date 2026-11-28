package com.conxist.runs.domain;

import com.conxist.connectors.McpToolbox;
import com.conxist.platform.RuleViolation;

/**
 * What a workspace adds to ConXist's own guardrail (ADR 0025). Its rules come after ConXist's and can only add limits;
 * its limit on what one call to an outside tool may send can only be lower than the toolbox's own.
 *
 * @param rules              the workspace's rules, as written; may be empty
 * @param maxInputCharacters the most characters one outside tool call may send
 */
public record Guardrails(String rules, int maxInputCharacters) {

    public static final int MAX_RULES = 2_000;
    public static final int MIN_INPUT_CHARACTERS = 200;
    public static final int MAX_INPUT_CHARACTERS = McpToolbox.MAX_ARGUMENT_CHARACTERS;
    /** What a workspace that never changed them uses: no rules of its own, ConXist's limit. */
    public static final Guardrails DEFAULT = new Guardrails("", MAX_INPUT_CHARACTERS);

    public Guardrails {
        rules = rules == null ? "" : rules.replace("\r\n", "\n").strip();
        if (rules.length() > MAX_RULES) {
            throw new RuleViolation("guardrails.rules-too-long",
                    "Workspace rules can be up to " + MAX_RULES + " characters.");
        }
        if (maxInputCharacters < MIN_INPUT_CHARACTERS || maxInputCharacters > MAX_INPUT_CHARACTERS) {
            throw new RuleViolation("guardrails.input-limit", "The most text one call may send must be between "
                    + MIN_INPUT_CHARACTERS + " and " + MAX_INPUT_CHARACTERS + " characters.");
        }
    }
}
