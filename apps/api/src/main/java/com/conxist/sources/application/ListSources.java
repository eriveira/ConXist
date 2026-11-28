package com.conxist.sources.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.conxist.platform.CurrentWorkspace;
import com.conxist.sources.SourceCatalog;
import com.conxist.sources.SourceSummary;
import com.conxist.sources.domain.Source;

/** Answers which sources the current workspace has connected. */
public final class ListSources implements SourceCatalog {

    private final SourceRepository sources;
    private final CurrentWorkspace workspace;

    public ListSources(SourceRepository sources, CurrentWorkspace workspace) {
        this.sources = sources;
        this.workspace = workspace;
    }

    @Override
    public List<SourceSummary> list() {
        return sources.findAll(workspace.id()).stream().map(Source::toSummary).toList();
    }

    @Override
    public Optional<SourceSummary> find(UUID sourceId) {
        return sources.find(workspace.id(), sourceId).map(Source::toSummary);
    }
}
