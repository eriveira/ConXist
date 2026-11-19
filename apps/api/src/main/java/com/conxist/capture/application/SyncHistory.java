package com.conxist.capture.application;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Every check of a meeting recorder, for the source's import history: when, who started it, what came of it. Counts
 * and a problem code only, never a title or text. Kept for {@link #KEEP}.
 */
public interface SyncHistory {

    Duration KEEP = Duration.ofDays(90);

    void record(SyncRun run);

    /** Newest first, at most {@code limit}; with {@code changesOnly}, checks that found nothing new are left out. */
    List<SyncRun> recent(UUID workspaceId, UUID sourceId, boolean changesOnly, int limit);

    /** Deletes checks that started before {@code before}. */
    void forgetBefore(UUID workspaceId, Instant before);

    enum StartedBy { SCHEDULE, PERSON }

    enum Outcome { IMPORTED, UP_TO_DATE, FAILED }

    /** {@code problemCode} is set only when the check failed. */
    record SyncRun(UUID id, UUID workspaceId, UUID sourceId, Instant startedAt, Instant finishedAt, StartedBy startedBy,
            Outcome outcome, int imported, int alreadyImported, int waiting, int queued, String problemCode) {
    }
}
