package com.conxist.platform;

import java.util.List;
import java.util.UUID;

/** Which workspaces exist. The workspaces module answers; the platform uses it to check the workspace a request names. */
public interface WorkspaceDirectory {

    boolean exists(UUID workspaceId);

    /** Every workspace, for work that runs outside a request (the scheduled recorder check). */
    List<UUID> ids();
}
