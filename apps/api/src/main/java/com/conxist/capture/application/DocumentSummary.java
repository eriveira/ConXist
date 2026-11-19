package com.conxist.capture.application;

import java.time.Instant;
import java.util.UUID;
import com.conxist.capture.DocumentOrigin;
import com.conxist.capture.domain.CapturedDocument;
import com.conxist.capture.domain.MeetingText;

/**
 * A captured document without its text. {@code recordedAt} is when a recorded meeting took place (null for an upload),
 * which is not when it was imported.
 */
public record DocumentSummary(UUID id, UUID sourceId, String title, String mediaType, long sizeBytes,
        DocumentOrigin origin, int textLength, Instant capturedAt, Instant recordedAt) {

    static DocumentSummary of(CapturedDocument document) {
        Instant recordedAt = document.origin() == DocumentOrigin.RECORDING
                ? MeetingText.recordedAt(document.text()) : null;
        return new DocumentSummary(document.id(), document.sourceId(), document.title().value(), document.mediaType(),
                document.sizeBytes(), document.origin(), document.text().length(), document.capturedAt(), recordedAt);
    }
}
