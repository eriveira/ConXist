package com.conxist.sources;

import java.time.Instant;
import java.util.UUID;

/** A connected source as other modules and the API see it. */
public record SourceSummary(
        UUID id,
        SourceCategory category,
        Provider provider,
        String name,
        String accountLabel,
        Instant addedAt) {
}
