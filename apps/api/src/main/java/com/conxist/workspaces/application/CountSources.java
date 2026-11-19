package com.conxist.workspaces.application;

import com.conxist.sources.SourceAdded;

/** Keeps each workspace's source count, from the sources module's events. */
public final class CountSources {

    private final WorkspaceRepository workspaces;

    public CountSources(WorkspaceRepository workspaces) {
        this.workspaces = workspaces;
    }

    public void sourceAdded(SourceAdded event) {
        workspaces.recordSource(event.workspaceId(), event.sourceId());
    }
}
