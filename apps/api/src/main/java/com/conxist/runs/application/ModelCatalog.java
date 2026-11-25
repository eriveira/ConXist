package com.conxist.runs.application;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The models agents may run on, and where a template's tier starts an agent (ADR 0014). Both come from the API's
 * settings; an agent keeps its own choice after it is created.
 */
public record ModelCatalog(List<ModelOption> models, Map<Preset, ModelStart> tiers) {

    public ModelCatalog {
        models = List.copyOf(models);
        tiers = Map.copyOf(tiers);
        for (Preset preset : Preset.values()) {
            ModelStart start = tiers.get(preset);
            if (start == null || models.stream().noneMatch(m -> m.provider().equals(start.provider())
                    && m.id().equals(start.model()))) {
                throw new IllegalArgumentException("Each tier must start on a listed model: " + preset);
            }
        }
    }

    Optional<ModelOption> option(String provider, String id) {
        return models.stream().filter(m -> m.provider().equals(provider) && m.id().equals(id)).findFirst();
    }

    ModelStart tier(Preset preset) {
        return tiers.get(preset);
    }

    /** The providers the models belong to, in the order the settings list them. */
    List<String> providers() {
        return models.stream().map(ModelOption::provider).distinct().toList();
    }
}
