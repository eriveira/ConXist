package com.conxist.runs.application;

import java.util.Arrays;
import java.util.Optional;

/** A template's starting tier (ADR 0014): fast, balanced or thorough work. Matches the agents module's setting. */
public enum Preset {
    FAST, BALANCED, THOROUGH;

    /** The tier with this name, or empty for anything else. */
    public static Optional<Preset> named(String name) {
        return Arrays.stream(values()).filter(preset -> preset.name().equals(name)).findFirst();
    }
}
