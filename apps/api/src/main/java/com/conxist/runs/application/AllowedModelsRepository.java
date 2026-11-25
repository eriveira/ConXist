package com.conxist.runs.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The models a workspace ticked or unticked in Settings (ADR 0014). A model with no choice saved is allowed when the
 * API's settings list it.
 */
public interface AllowedModelsRepository {

    List<Choice> find(UUID workspaceId);

    void save(UUID workspaceId, Choice choice, Instant at);

    /** {@code model} keeps the name the provider gave it, so an allowed model shows without asking the provider. */
    record Choice(String provider, ProviderModel model, boolean allowed) {
    }
}
