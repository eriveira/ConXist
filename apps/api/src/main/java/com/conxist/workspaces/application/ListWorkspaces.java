package com.conxist.workspaces.application;

import java.util.List;
import java.util.UUID;
import com.conxist.platform.WorkspaceDirectory;

/** Answers which workspaces exist, for the switcher and for the platform's check of each request. */
public final class ListWorkspaces implements WorkspaceDirectory {

    private final WorkspaceRepository workspaces;

    public ListWorkspaces(WorkspaceRepository workspaces) {
        this.workspaces = workspaces;
    }

    public List<WorkspaceView> list() {
        return workspaces.findAll();
    }

    @Override
    public boolean exists(UUID workspaceId) {
        return workspaces.exists(workspaceId);
    }

    @Override
    public List<UUID> ids() {
        return workspaces.findAll().stream().map(WorkspaceView::id).toList();
    }
}
