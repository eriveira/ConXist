package com.conxist.workspaces.application;

import java.time.Instant;
import java.util.UUID;

/** A workspace as the app shows it in the switcher. */
public record WorkspaceView(UUID id, String name, String description, int sourceCount, Instant createdAt) {
}
