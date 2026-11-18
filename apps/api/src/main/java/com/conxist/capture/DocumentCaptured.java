package com.conxist.capture;

import java.time.Instant;
import java.util.UUID;

/** Published when a document's text has been captured and is waiting for review in the hub. */
public record DocumentCaptured(UUID documentId, UUID workspaceId, UUID sourceId, String title, DocumentOrigin origin,
        Instant capturedAt) {
}
