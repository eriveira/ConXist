package com.conxist.platform;

import java.util.Map;
import java.util.UUID;

/**
 * A workspace's variables (ADR 0018), for the modules that fill {@code {{name}}} in instructions. The workspaces module
 * answers. Keys are the names in lower case, since names are unique ignoring case.
 */
public interface WorkspaceVariables {

    Map<String, String> of(UUID workspaceId);

    static WorkspaceVariables none() {
        return workspaceId -> Map.of();
    }
}
