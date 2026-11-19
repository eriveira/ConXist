package com.conxist.capture.application;

import java.util.UUID;
import com.conxist.platform.NotFound;
import com.conxist.platform.RuleViolation;
import com.conxist.sources.Provider;
import com.conxist.sources.SourceCatalog;
import com.conxist.sources.SourceSummary;

/** Finds the source a capture goes to and checks it is the right kind. */
final class CaptureSources {

    private final SourceCatalog catalog;

    CaptureSources(SourceCatalog catalog) {
        this.catalog = catalog;
    }

    SourceSummary find(UUID sourceId) {
        return catalog.find(sourceId).orElseThrow(() -> new NotFound("source.not-found", "This source no longer exists."));
    }

    SourceSummary require(UUID sourceId, Provider expected) {
        SourceSummary source = find(sourceId);
        if (source.provider() != expected) {
            throw new RuleViolation("source.wrong-kind", "This source does not accept " + expected.displayName() + " captures.");
        }
        return source;
    }
}
