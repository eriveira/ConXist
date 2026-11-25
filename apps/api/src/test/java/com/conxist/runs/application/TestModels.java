package com.conxist.runs.application;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** The models the API's settings list by default, and a workspace default kept in memory. */
final class TestModels {

    static final String ANTHROPIC = "anthropic";
    static final String HAIKU = "claude-haiku-5-5";
    static final String SONNET = "claude-sonnet-5-5";
    static final String OPUS = "claude-opus-5-5";

    private TestModels() {
    }

    static ModelCatalog catalog() {
        return new ModelCatalog(List.of(new ModelOption(ANTHROPIC, HAIKU, "Claude Haiku 5.5", false, 8_000),
                new ModelOption(ANTHROPIC, SONNET, "Claude Sonnet 5.5", true, 16_000),
                new ModelOption(ANTHROPIC, OPUS, "Claude Opus 5.5", true, 16_000)),
                new ModelStart(ANTHROPIC, SONNET, ModelChoice.Effort.MEDIUM));
    }

    /** Defaults kept in memory, one per workspace. */
    static final class Defaults implements ModelDefaultsRepository {
        final Map<UUID, ModelStart> saved = new HashMap<>();

        @Override
        public Optional<ModelStart> find(UUID workspaceId) {
            return Optional.ofNullable(saved.get(workspaceId));
        }

        @Override
        public void save(UUID workspaceId, ModelStart start, Instant at) {
            saved.put(workspaceId, start);
        }
    }
}
