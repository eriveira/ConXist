package com.conxist.runs.application;

import java.time.Clock;
import java.time.Instant;
import com.conxist.platform.Conflict;
import com.conxist.platform.CurrentWorkspace;
import com.conxist.runs.domain.Guardrails;

/** The current workspace's guardrails (ADR 0025): its rules, its version of ConXist's rule, and its limits. */
public final class WorkspaceGuardrails {

    private static final System.Logger log = System.getLogger(WorkspaceGuardrails.class.getName());

    private final GuardrailsRepository guardrails;
    private final CurrentWorkspace workspace;
    private final Clock clock;

    public WorkspaceGuardrails(GuardrailsRepository guardrails, CurrentWorkspace workspace, Clock clock) {
        this.guardrails = guardrails;
        this.workspace = workspace;
        this.clock = clock;
    }

    /** The workspace's own, or none and ConXist's limit when it never changed them. */
    public Guardrails current() {
        return saved().guardrails();
    }

    /** The current guardrails with the revision a change must name; revision 0 when the workspace never saved any. */
    public GuardrailsRepository.Saved saved() {
        return guardrails.find(workspace.id()).orElse(new GuardrailsRepository.Saved(Guardrails.DEFAULT, 0));
    }

    /**
     * Saves what the workspace set. A version of ConXist's rule identical to ConXist's own counts as ConXist's, so a
     * workspace that saves it unchanged is not marked as edited; a new version records when it was saved.
     */
    public GuardrailsRepository.Saved change(Guardrails changed, long revision) {
        Instant now = clock.instant();
        GuardrailsRepository.Saved current = saved();
        if (current.revision() != revision) {
            throw stale();
        }
        Guardrails saved = changed.savedOver(current.guardrails(), now);
        if (!guardrails.save(workspace.id(), saved, revision, now)) {
            throw stale();
        }
        // Sizes only: the rules are the workspace's own words.
        log.log(System.Logger.Level.INFO, "Workspace {0} changed its guardrails: {1} characters of rules, {2} per call, "
                + "ConXist''s rule {3}, copy guard {4} words", workspace.id(), saved.rules().length(),
                saved.maxInputCharacters(), saved.baseRuleEdited() ? "edited (" + saved.baseRule().length()
                        + " characters)" : "as ConXist wrote it", saved.copyGuardWords());
        return new GuardrailsRepository.Saved(saved, revision + 1);
    }

    /**
     * Every save sends all the settings, so one made from values another tab has since changed would quietly undo that
     * change, a stricter copy guard included; it is refused instead.
     */
    private static Conflict stale() {
        return new Conflict("guardrails.changed", "These guardrails changed in another tab or window. Reload to see"
                + " them, then save again.");
    }
}
