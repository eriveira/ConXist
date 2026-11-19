package com.conxist.capture.adapter.schedule;

import java.util.List;
import java.util.UUID;
import com.conxist.capture.application.SyncRecorder;
import com.conxist.capture.application.SyncRecorder.SourceSync;
import com.conxist.platform.WorkspaceDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Checks every active meeting recorder of every workspace, so recordings arrive without anyone pressing Sync. */
@Component
class ScheduledRecorderSync {

    private static final Logger log = LoggerFactory.getLogger(ScheduledRecorderSync.class);

    /** The same log key the API uses for a request id, so one run's lines can be found together. */
    private static final String JOB_LOG_KEY = "requestId";

    private final SyncRecorder syncRecorder;
    private final WorkspaceDirectory workspaces;

    ScheduledRecorderSync(SyncRecorder syncRecorder, WorkspaceDirectory workspaces) {
        this.syncRecorder = syncRecorder;
        this.workspaces = workspaces;
    }

    @Scheduled(initialDelayString = "${conxist.plaud.sync-interval}", fixedDelayString = "${conxist.plaud.sync-interval}")
    void checkRecorders() {
        MDC.put(JOB_LOG_KEY, "recorder-sync-" + UUID.randomUUID());
        try {
            workspaces.ids().forEach(workspaceId -> report(syncRecorder.syncAll(workspaceId)));
        } finally {
            MDC.remove(JOB_LOG_KEY);
        }
    }

    private static void report(List<SourceSync> syncs) {
        for (SourceSync sync : syncs) {
            if (sync.problemCode() != null) {
                log.warn("Recorder source {} not synced: {}", sync.sourceId(), sync.problemCode());
            } else if (sync.result().imported() > 0) {
                log.info("Recorder source {} imported {} meetings", sync.sourceId(), sync.result().imported());
            }
        }
    }
}
