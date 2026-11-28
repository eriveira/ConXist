package com.conxist.runs.domain;

import java.time.Instant;
import com.conxist.platform.RuleViolation;

/**
 * How a workspace set up Tuga (ADR 0027): the model it answers on, and its prompt.
 *
 * @param provider        the provider Tuga answers on, or {@code null} until the workspace picks one; Tuga then uses
 *                        where new agents start
 * @param model           the model, set together with {@code provider}
 * @param effort          {@code LOW}, {@code MEDIUM} or {@code HIGH}, set together with {@code provider}
 * @param prompt          the workspace's version of Tuga's prompt, or empty for ConXist's (ConXist's text saved as it is
 *                        counts as empty, so it keeps getting ConXist's updates)
 * @param promptChangedAt when the workspace last saved its own version, or {@code null} when it uses ConXist's
 */
public record TugaSettings(String provider, String model, String effort, String prompt, Instant promptChangedAt) {

    /** What a workspace that never set up Tuga uses: where new agents start, and ConXist's prompt. */
    public static final TugaSettings DEFAULT = new TugaSettings(null, null, null, "", null);

    public TugaSettings {
        if ((provider == null) != (model == null) || (provider == null) != (effort == null)) {
            throw new RuleViolation("tuga.model-incomplete", "Pick a provider, a model and an effort for Tuga.");
        }
        if (effort != null && !effort.matches("LOW|MEDIUM|HIGH")) {
            throw new RuleViolation("model.unknown-effort", "Pick low, medium or high effort.");
        }
        // A NUL can't be stored, and no prompt needs one.
        prompt = prompt == null ? "" : prompt.replace("\r\n", "\n").replace("\0", "").strip();
        if (!prompt.isEmpty() && TugaPrompt.isDefault(prompt)) {
            prompt = "";
        }
        // Counted in characters as people see them, as the app counts them, so an emoji is one.
        if (prompt.codePointCount(0, prompt.length()) > TugaPrompt.MAX_PROMPT) {
            throw new RuleViolation("tuga.prompt-too-long",
                    "Tuga's prompt can be up to " + TugaPrompt.MAX_PROMPT + " characters.");
        }
        if (prompt.isEmpty()) {
            promptChangedAt = null;
        }
    }

    /**
     * These settings saved over {@code before} at {@code at}: the prompt's date moves only when its text differs, and
     * goes when the workspace is back on ConXist's prompt.
     */
    public TugaSettings savedOver(TugaSettings before, Instant at) {
        Instant changedAt = prompt.equals(before.prompt) ? before.promptChangedAt : at;
        return new TugaSettings(provider, model, effort, prompt, changedAt);
    }

    /** Whether the workspace replaced ConXist's prompt with its own version. */
    public boolean promptEdited() {
        return !prompt.isEmpty();
    }

    /** Whether the workspace picked Tuga's model; if not, Tuga answers where new agents start. */
    public boolean modelPicked() {
        return provider != null;
    }
}
