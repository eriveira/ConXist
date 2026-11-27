package com.conxist.runs.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Each provider's default model in a workspace, and the provider its new blank agents start on (ADR 0014). */
public interface ModelDefaultsRepository {

    Saved find(UUID workspaceId);

    /** Saves the start as its provider's default. */
    void save(UUID workspaceId, ModelStart start, Instant at);

    /** Saves the start as its provider's default and makes that provider the one new agents start on. */
    void startNewAgentsOn(UUID workspaceId, ModelStart start, Instant at);

    /**
     * @param providers one start per provider the workspace picked a default for
     * @param newAgents the provider new agents start on; null until the workspace picks one
     */
    record Saved(List<ModelStart> providers, String newAgents) {

        public Saved {
            providers = List.copyOf(providers);
        }

        static Saved none() {
            return new Saved(List.of(), null);
        }
    }
}
