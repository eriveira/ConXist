package com.conxist.platform;

import java.util.UUID;

/**
 * Deletes everything one module keeps for a workspace, when the workspace is deleted for good. Each module answers for
 * its own tables; the workspaces module calls them all in one transaction, in their order, then deletes the workspace.
 */
public interface WorkspaceData {

    /** Rows that point at another module's rows go first, so a lower number runs first. */
    int order();

    void deleteAll(UUID workspaceId);

    /** Removes what is kept outside the database, such as stored files, once the deletion has committed. */
    default void afterDeleted(UUID workspaceId) {
    }
}
