package com.conxist.sources;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Lets other modules list and look up the sources of the current workspace. */
public interface SourceCatalog {

    List<SourceSummary> list();

    Optional<SourceSummary> find(UUID sourceId);
}
