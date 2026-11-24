package com.conxist.runs.application;

import java.util.List;
import java.util.Optional;

/**
 * What each preset an agent picks (Fast, Balanced, Thorough) runs on unless the workspace picked another model, and
 * the models a workspace may pick from (ADR 0012, ADR 0014). Effort and the token limit belong to the preset.
 */
public record ModelPresets(ModelChoice fast, ModelChoice balanced, ModelChoice thorough, List<ModelOption> models) {

    public ModelPresets {
        models = List.copyOf(models);
        List<String> listed = models.stream().map(ModelOption::id).toList();
        for (ModelChoice preset : List.of(fast, balanced, thorough)) {
            if (!listed.contains(preset.id())) {
                throw new IllegalArgumentException("A preset's model must be one of the listed models: " + preset.id());
            }
        }
    }

    ModelChoice of(Preset preset) {
        return switch (preset) {
            case FAST -> fast;
            case BALANCED -> balanced;
            case THOROUGH -> thorough;
        };
    }

    /** The preset with the model the workspace picked, when it is still listed; else the preset as set up. */
    ModelChoice of(Preset preset, Optional<String> picked) {
        ModelChoice base = of(preset);
        return picked.flatMap(this::option)
                .map(model -> new ModelChoice(model.id(), base.effort(), model.fallbacks(), base.maxTokens()))
                .orElse(base);
    }

    Optional<ModelOption> option(String id) {
        return models.stream().filter(model -> model.id().equals(id)).findFirst();
    }
}
