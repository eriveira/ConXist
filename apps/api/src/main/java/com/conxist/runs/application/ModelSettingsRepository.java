package com.conxist.runs.application;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** The model each workspace picked for a preset (ADR 0014). A preset with no pick runs on its default. */
public interface ModelSettingsRepository {

    /** Preset name to model id. */
    Map<String, String> picks(UUID workspaceId);

    void pick(UUID workspaceId, String preset, String modelId, Instant at);
}
