package com.conxist.sources;

import java.time.Instant;
import java.util.UUID;

/** Published when a source is connected to the hub. */
public record SourceAdded(UUID sourceId, UUID workspaceId, Provider provider, String name, Instant addedAt) {
}
