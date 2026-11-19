package com.conxist.capture;

import java.time.Instant;
import java.util.UUID;

/** Published when a person deleted a captured document from ConXist. Carries no title or text. */
public record DocumentDeleted(UUID documentId, UUID workspaceId, UUID sourceId, Instant deletedAt) {
}
