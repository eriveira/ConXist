package com.conxist.sources;

import java.util.Optional;
import java.util.UUID;

/** Lets other modules look up a source of the current workspace. */
public interface SourceCatalog {

    Optional<SourceSummary> find(UUID sourceId);
}
