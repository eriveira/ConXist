package com.conxist.workspaces.application;

import java.util.List;
import java.util.UUID;
import com.conxist.workspaces.domain.Workspace;

public interface WorkspaceRepository {

    /** Saves it unless another workspace has the name (ignoring case), which can happen when two creates race. */
    boolean saveIfNameFree(Workspace workspace);

    /** Oldest first, each with how many sources it has. */
    List<WorkspaceView> findAll();

    /** Every workspace id, oldest first. */
    List<UUID> ids();

    boolean exists(UUID workspaceId);

    boolean nameTaken(String name);

    /** Counts a source towards its workspace. Safe to call twice for the same source. */
    void recordSource(UUID workspaceId, UUID sourceId);
}
