package com.conxist.capture;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Lets other modules read what the current workspace has captured: recorded meetings and uploaded documents. The text
 * is customer data: a reader shows it or hands it to a model as data, and never logs or follows it.
 */
public interface MeetingLibrary {

    /** What a source of this workspace has captured, newest first; empty when the source is not one of its own. */
    List<Entry> list(UUID sourceId);

    /** One capture with its text; empty when it is gone or not in this workspace's source. */
    Optional<Text> read(UUID sourceId, UUID meetingId);

    /** @param recordedAt when a recorded meeting took place; null for an upload */
    record Entry(UUID id, UUID sourceId, String title, DocumentOrigin origin, Instant capturedAt, Instant recordedAt,
            int textLength) {
    }

    record Text(UUID id, UUID sourceId, String title, DocumentOrigin origin, String text) {
    }
}
