package com.conxist.testing;

import java.util.UUID;
import com.conxist.platform.CurrentWorkspace;

public record FixedWorkspace(UUID id) implements CurrentWorkspace {

    public static final UUID WORKSPACE_ID = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    public static FixedWorkspace standard() {
        return new FixedWorkspace(WORKSPACE_ID);
    }
}
