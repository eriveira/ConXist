package com.conxist.capture.domain;

import java.time.Instant;
import java.util.UUID;
import com.conxist.capture.DocumentOrigin;

/**
 * Text captured from one file or recording. {@code originReference} is the stored file's key for an upload and the
 * recorder's recording id for a recording, so the same recording is never imported twice.
 */
public record CapturedDocument(
        UUID id,
        UUID workspaceId,
        UUID sourceId,
        DocumentTitle title,
        String mediaType,
        long sizeBytes,
        String text,
        DocumentOrigin origin,
        String originReference,
        Instant capturedAt) {
}
