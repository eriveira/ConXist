package com.conxist.runs.domain;

import java.time.Instant;
import com.conxist.connectors.McpToolbox;
import com.conxist.platform.RuleViolation;

/**
 * What a workspace sets on top of, or in place of, ConXist's own guardrail (ADR 0025). Its rules come after ConXist's
 * rule; its limits on outside calls can only be stricter than ConXist's. Since the owner's change of 2026-11-28 a
 * workspace may also replace ConXist's rule with its own text, after a warning, and restore it at any time; the code
 * checks (copy guard and size) run whatever the rule says.
 *
 * @param rules              the workspace's rules, as written; may be empty
 * @param maxInputCharacters the most characters one outside tool call may send
 * @param baseRule           the workspace's own version of ConXist's rule, or empty for ConXist's current one (ConXist's
 *                           text saved unchanged counts as empty)
 * @param baseRuleChangedAt  when the workspace last saved its own version, or {@code null} when it uses ConXist's
 * @param copyWords          how many words in a row count as copying, or {@code null} for ConXist's standard
 */
public record Guardrails(String rules, int maxInputCharacters, String baseRule, Instant baseRuleChangedAt,
        Integer copyWords) {

    public static final int MAX_RULES = 2_000;
    public static final int MAX_BASE_RULE = 4_000;
    public static final int MIN_INPUT_CHARACTERS = 200;
    public static final int MAX_INPUT_CHARACTERS = McpToolbox.MAX_ARGUMENT_CHARACTERS;
    /** A workspace may only make the copy guard stricter than ConXist's standard. */
    public static final int MIN_COPY_WORDS = 6;
    public static final int MAX_COPY_WORDS = CopyGuard.SHINGLE_WORDS - 1;
    /** What a workspace that never changed them uses: ConXist's rule and limits, no rules of its own. */
    public static final Guardrails DEFAULT = new Guardrails("", MAX_INPUT_CHARACTERS);

    public Guardrails {
        rules = normalized(rules);
        baseRule = normalized(baseRule);
        // ConXist's own text saved as it is counts as ConXist's rule, so it is never marked edited and keeps updating.
        if (!baseRule.isEmpty() && RunPrompt.isConxistRule(baseRule)) {
            baseRule = "";
        }
        // Counted in characters as people see them, as the app counts them, so an emoji is one.
        if (rules.codePointCount(0, rules.length()) > MAX_RULES) {
            throw new RuleViolation("guardrails.rules-too-long",
                    "Workspace rules can be up to " + MAX_RULES + " characters.");
        }
        if (baseRule.codePointCount(0, baseRule.length()) > MAX_BASE_RULE) {
            throw new RuleViolation("guardrails.base-rule-too-long",
                    "ConXist's rule can be up to " + MAX_BASE_RULE + " characters.");
        }
        if (maxInputCharacters < MIN_INPUT_CHARACTERS || maxInputCharacters > MAX_INPUT_CHARACTERS) {
            throw new RuleViolation("guardrails.input-limit", "The most text one call may send must be between "
                    + MIN_INPUT_CHARACTERS + " and " + MAX_INPUT_CHARACTERS + " characters.");
        }
        if (copyWords != null && (copyWords < MIN_COPY_WORDS || copyWords > MAX_COPY_WORDS)) {
            throw new RuleViolation("guardrails.copy-words", "Words in a row that count as copying must be between "
                    + MIN_COPY_WORDS + " and " + MAX_COPY_WORDS + ", or empty for " + CopyGuard.SHINGLE_WORDS + ".");
        }
        if (baseRule.isEmpty()) {
            baseRuleChangedAt = null;
        }
    }

    /** Rules and a limit, with ConXist's rule and copy guard as they are. */
    public Guardrails(String rules, int maxInputCharacters) {
        this(rules, maxInputCharacters, "", null, null);
    }

    /**
     * The same settings with a new version of ConXist's rule saved at {@code at}: the date moves only when the text
     * differs from this one's, and goes when the workspace is back on ConXist's rule.
     */
    public Guardrails savedOver(Guardrails before, Instant at) {
        Instant changedAt = baseRule.equals(before.baseRule) ? before.baseRuleChangedAt : at;
        return new Guardrails(rules, maxInputCharacters, baseRule, changedAt, copyWords);
    }

    /** The same settings with other workspace rules. */
    public Guardrails withRules(String changed) {
        return new Guardrails(changed, maxInputCharacters, baseRule, baseRuleChangedAt, copyWords);
    }

    /** Whether the workspace replaced ConXist's rule with its own version. */
    public boolean baseRuleEdited() {
        return !baseRule.isEmpty();
    }

    /** How many words in a row the copy guard refuses for this workspace. */
    public int copyGuardWords() {
        return copyWords == null ? CopyGuard.SHINGLE_WORDS : copyWords;
    }

    private static String normalized(String text) {
        // A NUL can't be stored, and no rule needs one.
        return text == null ? "" : text.replace("\r\n", "\n").replace("\0", "").strip();
    }
}
