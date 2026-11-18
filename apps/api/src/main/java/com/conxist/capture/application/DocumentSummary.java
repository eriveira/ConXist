package com.conxist.capture.application;

import java.time.Instant;
import java.util.UUID;
import com.conxist.capture.DocumentOrigin;
import com.conxist.capture.domain.CapturedDocument;

/** A captured document without its text, which stays inside ConXist. */
public record DocumentSummary(UUID id, UUID sourceId, String title, String mediaType, long sizeBytes,
        DocumentOrigin origin, int textLength, Instant capturedAt) {

    static DocumentSummary of(CapturedDocument document) {
        return new DocumentSummary(document.id(), document.sourceId(), document.title().value(), document.mediaType(),
                document.sizeBytes(), document.origin(), document.text().length(), document.capturedAt());
    }
}
