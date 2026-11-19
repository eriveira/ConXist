package com.conxist.capture.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.conxist.capture.application.SyncHistory.Outcome;
import com.conxist.capture.application.SyncHistory.StartedBy;
import com.conxist.capture.application.SyncHistory.SyncRun;
import com.conxist.platform.CurrentWorkspace;
import com.conxist.sources.SourceCatalog;

/** A source's import history, newest first. */
public final class ListSyncHistory {

    /** The most checks one request returns: at one check every 5 minutes, more than half a day of quiet checks. */
    public static final int MAX_RUNS = 200;

    private final CaptureSources sources;
    private final SyncHistory history;
    private final CurrentWorkspace workspace;

    public ListSyncHistory(SourceCatalog catalog, SyncHistory history, CurrentWorkspace workspace) {
        this.sources = new CaptureSources(catalog);
        this.history = history;
        this.workspace = workspace;
    }

    public List<SyncRunView> list(UUID sourceId, boolean changesOnly) {
        sources.find(sourceId);
        return history.recent(workspace.id(), sourceId, changesOnly, MAX_RUNS).stream().map(SyncRunView::of).toList();
    }

    public record SyncRunView(UUID id, Instant startedAt, Instant finishedAt, StartedBy startedBy, Outcome outcome,
            int imported, int alreadyImported, int waiting, int queued, String problemCode) {

        static SyncRunView of(SyncRun run) {
            return new SyncRunView(run.id(), run.startedAt(), run.finishedAt(), run.startedBy(), run.outcome(),
                    run.imported(), run.alreadyImported(), run.waiting(), run.queued(), run.problemCode());
        }
    }
}
