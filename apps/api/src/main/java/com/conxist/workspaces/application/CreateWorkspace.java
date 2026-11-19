package com.conxist.workspaces.application;

import java.time.Clock;
import java.util.UUID;
import com.conxist.platform.UnitOfWork;
import com.conxist.workspaces.domain.InvalidWorkspace;
import com.conxist.workspaces.domain.Workspace;

/** Creates a workspace with a name and an optional description. */
public final class CreateWorkspace {

    private final WorkspaceRepository workspaces;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public CreateWorkspace(WorkspaceRepository workspaces, UnitOfWork unitOfWork, Clock clock) {
        this.workspaces = workspaces;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    public WorkspaceView create(String name, String description) {
        Workspace workspace = Workspace.create(UUID.randomUUID(), name, description, clock.instant());
        return unitOfWork.inTransaction(() -> {
            if (workspaces.nameTaken(workspace.name())) {
                throw new InvalidWorkspace("name-taken",
                        "A workspace named \"" + workspace.name() + "\" already exists.");
            }
            workspaces.save(workspace);
            return new WorkspaceView(workspace.id(), workspace.name(), workspace.description(), 0,
                    workspace.createdAt());
        });
    }
}
