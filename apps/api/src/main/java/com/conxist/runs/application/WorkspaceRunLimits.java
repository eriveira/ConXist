package com.conxist.runs.application;

import java.time.Clock;
import com.conxist.platform.CurrentWorkspace;
import com.conxist.runs.domain.RunLimits;

/** The current workspace's run limits, which every agent run in it keeps (ADR 0023), and changing them. */
public final class WorkspaceRunLimits {

    private final RunLimitsRepository limits;
    private final CurrentWorkspace workspace;
    private final Clock clock;

    public WorkspaceRunLimits(RunLimitsRepository limits, CurrentWorkspace workspace, Clock clock) {
        this.limits = limits;
        this.workspace = workspace;
        this.clock = clock;
    }

    /** The workspace's limits, or ConXist's defaults when it never changed them. */
    public RunLimits current() {
        return limits.find(workspace.id()).orElse(RunLimits.DEFAULT);
    }

    public RunLimits change(RunLimits changed) {
        limits.save(workspace.id(), changed, clock.instant());
        return changed;
    }
}
