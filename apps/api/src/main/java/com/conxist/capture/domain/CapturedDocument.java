package com.conxist.capture.domain;

import java.time.Instant;
import java.util.UUID;
import com.conxist.capture.DocumentOrigin;

/**
 * Text captured from one file or recording. {@code originReference} is the stored file's key for an upload and the
 * recorder's recording id for a recording, so the same recording is never imported twice. {@code editedAt} is when a
 * person last corrected it in ConXist, or null while it is as imported.
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
        Instant capturedAt,
        Instant editedAt) {

    /** As captured, not edited yet. */
    public CapturedDocument(UUID id, UUID workspaceId, UUID sourceId, DocumentTitle title, String mediaType, long sizeBytes,
            String text, DocumentOrigin origin, String originReference, Instant capturedAt) {
        this(id, workspaceId, sourceId, title, mediaType, sizeBytes, text, origin, originReference, capturedAt, null);
    }
}
