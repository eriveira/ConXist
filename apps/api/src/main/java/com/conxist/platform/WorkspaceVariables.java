package com.conxist.platform;

import java.util.Map;
import java.util.UUID;

/**
 * A workspace's variables (ADR 0018), for the modules that fill {@code {{name}}} in instructions. The workspaces module
 * answers. Keys are the names in lower case, since names are unique ignoring case.
 */
public interface WorkspaceVariables {

    /** A variable's name: letters, digits and underscores, starting with a letter, so it reads the same in braces. */
    String NAME_PATTERN = "[A-Za-z][A-Za-z0-9_]*";

    Map<String, String> of(UUID workspaceId);
}
