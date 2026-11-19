package com.conxist.capture;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a person changed a captured document's title in ConXist, by editing it or by restoring the original.
 * Carries the new title so the hub shows the same name; never the text.
 */
public record DocumentRetitled(UUID documentId, UUID workspaceId, UUID sourceId, String title, Instant changedAt) {
}
