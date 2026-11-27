package com.conxist.workspaces.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.conxist.workspaces.domain.Workspace;

public interface WorkspaceRepository {

    /** Saves it unless another workspace has the name (ignoring case), which can happen when two creates race. */
    boolean saveIfNameFree(Workspace workspace);

    /** Saves a new name and description unless another workspace has the name (ignoring case). */
    boolean updateIfNameFree(Workspace workspace);

    Optional<WorkspaceView> find(UUID workspaceId);

    /** Oldest first, each with how many sources it has. */
    List<WorkspaceView> findAll();

    /** Every workspace id, oldest first. */
    List<UUID> ids();

    boolean exists(UUID workspaceId);

    boolean nameTaken(String name);

    /** Deletes the workspace with its variables and its record of sources. */
    void delete(UUID workspaceId);

    /** Counts a source towards its workspace. Safe to call twice for the same source. */
    void recordSource(UUID workspaceId, UUID sourceId);
}
