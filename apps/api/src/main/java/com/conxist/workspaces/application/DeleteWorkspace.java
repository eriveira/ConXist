package com.conxist.workspaces.application;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import com.conxist.platform.NotFound;
import com.conxist.platform.RuleViolation;
import com.conxist.platform.UnitOfWork;
import com.conxist.platform.WorkspaceData;

/**
 * Deletes a workspace for good with everything in it, once the person typed its name (owner, 2026-11-27). Every module
 * deletes its own data in one transaction, so a failure leaves the workspace as it was; stored files go after. The last workspace stays.
 */
public final class DeleteWorkspace {

    private static final System.Logger log = System.getLogger(DeleteWorkspace.class.getName());

    private final WorkspaceRepository workspaces;
    private final List<WorkspaceData> data;
    private final UnitOfWork unitOfWork;

    public DeleteWorkspace(WorkspaceRepository workspaces, List<WorkspaceData> data, UnitOfWork unitOfWork) {
        this.workspaces = workspaces;
        this.data = data.stream().sorted(Comparator.comparingInt(WorkspaceData::order)).toList();
        this.unitOfWork = unitOfWork;
    }

    /** @param typedName what the person typed to confirm; it must be the workspace's name exactly */
    public void delete(UUID id, String typedName) {
        unitOfWork.inTransaction(() -> {
            WorkspaceView workspace = workspaces.find(id)
                    .orElseThrow(() -> new NotFound("workspace.not-found", "This workspace no longer exists."));
            if (typedName == null || !workspace.name().equals(typedName.strip())) {
                throw new RuleViolation("workspace.name-mismatch", "Type the workspace's name exactly to delete it.");
            }
            if (workspaces.ids().size() <= 1) {
                throw new RuleViolation("workspace.last", "This is your only workspace. Create another one first.");
            }
            data.forEach(module -> module.deleteAll(id));
            workspaces.delete(id);
            return null;
        });
        for (WorkspaceData module : data) {
            try {
                module.afterDeleted(id);
            } catch (RuntimeException failure) {
                // The workspace is gone; what is left is unreachable. Say so, without stopping the others.
                log.log(System.Logger.Level.WARNING, "Workspace {0} is deleted but {1} could not finish: {2}", id,
                        module.getClass().getSimpleName(), failure.getClass().getSimpleName());
            }
        }
        log.log(System.Logger.Level.INFO, "Deleted workspace {0} and everything in it", id);
    }
}
