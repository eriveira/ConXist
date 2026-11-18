package com.conxist.capture.adapter.schedule;

import com.conxist.capture.application.SyncRecorder;
import com.conxist.capture.application.SyncRecorder.SourceSync;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Checks every active meeting recorder for new recordings, so they arrive without anyone pressing Sync. */
@Component
class ScheduledRecorderSync {

    private static final Logger log = LoggerFactory.getLogger(ScheduledRecorderSync.class);

    private final SyncRecorder syncRecorder;

    ScheduledRecorderSync(SyncRecorder syncRecorder) {
        this.syncRecorder = syncRecorder;
    }

    @Scheduled(initialDelayString = "${conxist.plaud.sync-interval}", fixedDelayString = "${conxist.plaud.sync-interval}")
    void checkRecorders() {
        for (SourceSync sync : syncRecorder.syncAll()) {
            if (sync.problemCode() != null) {
                log.warn("Recorder source {} not synced: {}", sync.sourceId(), sync.problemCode());
            } else if (sync.result().imported() > 0) {
                log.info("Recorder source {} imported {} meetings", sync.sourceId(), sync.result().imported());
            }
        }
    }
}
