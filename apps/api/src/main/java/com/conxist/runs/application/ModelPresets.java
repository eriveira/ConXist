package com.conxist.runs.application;

/** What each preset an agent picks (Fast, Balanced, Thorough) runs on. */
public record ModelPresets(ModelChoice fast, ModelChoice balanced, ModelChoice thorough) {

    ModelChoice of(String preset) {
        return switch (preset) {
            case "FAST" -> fast;
            case "THOROUGH" -> thorough;
            default -> balanced;
        };
    }
}
