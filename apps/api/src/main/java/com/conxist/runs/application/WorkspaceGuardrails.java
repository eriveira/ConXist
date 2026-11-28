package com.conxist.runs.application;

import java.time.Clock;
import com.conxist.platform.CurrentWorkspace;
import com.conxist.runs.domain.Guardrails;

/** The current workspace's own rules and limit on outside calls (ADR 0025), which every agent run in it keeps. */
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
        return guardrails.find(workspace.id()).orElse(Guardrails.DEFAULT);
    }

    public Guardrails change(Guardrails changed) {
        guardrails.save(workspace.id(), changed, clock.instant());
        // Sizes only: the rules are the workspace's own words.
        log.log(System.Logger.Level.INFO, "Workspace {0} changed its guardrails: {1} characters of rules, {2} per call",
                workspace.id(), changed.rules().length(), changed.maxInputCharacters());
        return changed;
    }
}
