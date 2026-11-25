package com.conxist.workspaces.application;

import java.util.UUID;
import com.conxist.platform.NotFound;
import com.conxist.platform.UnitOfWork;
import com.conxist.workspaces.domain.InvalidWorkspace;
import com.conxist.workspaces.domain.Workspace;

/** Gives a workspace a new name or description, under the same rules as a new one. */
public final class UpdateWorkspace {

    private final WorkspaceRepository workspaces;
    private final UnitOfWork unitOfWork;

    public UpdateWorkspace(WorkspaceRepository workspaces, UnitOfWork unitOfWork) {
        this.workspaces = workspaces;
        this.unitOfWork = unitOfWork;
    }

    public WorkspaceView update(UUID id, String name, String description) {
        return unitOfWork.inTransaction(() -> {
            WorkspaceView current = workspaces.find(id)
                    .orElseThrow(() -> new NotFound("workspace.not-found", "This workspace no longer exists."));
            Workspace workspace = Workspace.create(id, name, description, current.createdAt());
            if (!workspaces.updateIfNameFree(workspace)) {
                throw new InvalidWorkspace("name-taken",
                        "A workspace named \"" + workspace.name() + "\" already exists.");
            }
            return new WorkspaceView(id, workspace.name(), workspace.description(), current.sourceCount(),
                    current.createdAt());
        });
    }
}
