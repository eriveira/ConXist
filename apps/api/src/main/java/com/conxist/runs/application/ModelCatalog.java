package com.conxist.runs.application;

import java.util.List;
import java.util.Optional;

/**
 * What the API's settings say about models (ADR 0014): the ones a workspace allows until it ticks its own, how to call
 * each (fallbacks, answer length), where a new agent starts, and the answer length of any other model a provider lists.
 */
public record ModelCatalog(List<ModelOption> models, ModelStart newAgents, long defaultMaxTokens) {

    public ModelCatalog {
        models = List.copyOf(models);
        if (newAgents == null || models.stream().noneMatch(m -> m.provider().equals(newAgents.provider())
                && m.id().equals(newAgents.model()))) {
            throw new IllegalArgumentException("New agents must start on a listed model: " + newAgents);
        }
        if (defaultMaxTokens <= 0) {
            throw new IllegalArgumentException("The default answer length must be positive.");
        }
    }

    /** One of the models the settings list. */
    Optional<ModelOption> option(String provider, String id) {
        return models.stream().filter(m -> m.provider().equals(provider) && m.id().equals(id)).findFirst();
    }

    /**
     * How to call a model: as the settings list it, or, for another model its provider offers, without fallbacks (not
     * every model takes them) and with the default answer length.
     */
    ModelOption callable(String provider, String id) {
        return option(provider, id).orElseGet(() -> new ModelOption(provider, id, id, false, defaultMaxTokens));
    }

    /** The providers the models belong to, in the order the settings list them. */
    List<String> providers() {
        return models.stream().map(ModelOption::provider).distinct().toList();
    }
}
