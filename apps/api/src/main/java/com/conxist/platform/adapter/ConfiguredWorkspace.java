package com.conxist.platform.adapter;

import java.util.UUID;
import com.conxist.platform.CurrentWorkspace;
import org.springframework.stereotype.Component;

/** Until sign-in exists every request acts for the one configured workspace. */
@Component
class ConfiguredWorkspace implements CurrentWorkspace {

    private final UUID workspaceId;

    ConfiguredWorkspace(WorkspaceProperties properties) {
        this.workspaceId = properties.defaultId();
    }

    @Override
    public UUID id() {
        return workspaceId;
    }
}
