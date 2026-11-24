package com.conxist.runs.application;

import java.util.Arrays;
import java.util.Optional;

/** How carefully an agent thinks, as it names it (ADR 0010): never a model. Matches the agents module's setting. */
public enum Preset {
    FAST, BALANCED, THOROUGH;

    /** The preset with this name, or empty for anything else. */
    public static Optional<Preset> named(String name) {
        return Arrays.stream(values()).filter(preset -> preset.name().equals(name)).findFirst();
    }
}
