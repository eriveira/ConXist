package com.conxist.runs.application;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import com.conxist.testing.TestClock;

/** The models the API's settings list by default, and a workspace's default and allowed models kept in memory. */
final class TestModels {

    static final String ANTHROPIC = "anthropic";
    static final String HAIKU = "claude-haiku-5-5";
    static final String SONNET = "claude-sonnet-5-5";
    static final String OPUS = "claude-opus-5-5";
    static final ApiKey COMPUTER_KEY = new ApiKey("sk-ant-computer-key-0001");

    private TestModels() {
    }

    static ModelCatalog catalog() {
        return new ModelCatalog(List.of(new ModelOption(ANTHROPIC, HAIKU, "Claude Haiku 5.5", false, 8_000),
                new ModelOption(ANTHROPIC, SONNET, "Claude Sonnet 5.5", true, 16_000),
                new ModelOption(ANTHROPIC, OPUS, "Claude Opus 5.5", true, 16_000)),
                new ModelStart(ANTHROPIC, SONNET, ModelChoice.Effort.MEDIUM), 16_000);
    }

    /** A model list kept for an hour that a refresh always asks for again, with a short time limit. */
    static ProviderModelLists lists() {
        return new ProviderModelLists(java.time.Duration.ofHours(1), java.time.Duration.ZERO,
                java.time.Duration.ofSeconds(5), TestClock.fixed());
    }

    /** This computer's keys: Anthropic's, which a test may take away. */
    static Map<String, ApiKey> computerKeys() {
        return new HashMap<>(Map.of(ANTHROPIC, COMPUTER_KEY));
    }

    /** Workspace keys kept in memory, in the clear: the encryption is the store's, tested with it. */
    static final class Keys implements ProviderKeysRepository {
        final Map<String, ApiKey> saved = new HashMap<>();
        final Map<String, KeyStatus> statuses = new HashMap<>();

        @Override
        public Optional<KeyStatus> status(UUID workspaceId, String provider) {
            return Optional.ofNullable(statuses.get(workspaceId + "/" + provider));
        }

        @Override
        public Optional<ApiKey> open(UUID workspaceId, String provider) {
            return Optional.ofNullable(saved.get(workspaceId + "/" + provider));
        }

        @Override
        public void save(UUID workspaceId, String provider, ApiKey key, Instant at) {
            saved.put(workspaceId + "/" + provider, key);
            statuses.put(workspaceId + "/" + provider, new KeyStatus(key.ending(), at, at, false));
        }

        @Override
        public void checked(UUID workspaceId, String provider, Instant at, boolean refused) {
            statuses.computeIfPresent(workspaceId + "/" + provider,
                    (id, status) -> new KeyStatus(status.ending(), status.addedAt(), at, refused));
        }

        @Override
        public boolean delete(UUID workspaceId, String provider) {
            statuses.remove(workspaceId + "/" + provider);
            return saved.remove(workspaceId + "/" + provider) != null;
        }
    }

    /** Ticks and unticks kept in memory. */
    static final class Allowed implements AllowedModelsRepository {
        final Map<UUID, Map<String, Choice>> saved = new HashMap<>();

        @Override
        public List<Choice> find(UUID workspaceId) {
            return List.copyOf(saved.getOrDefault(workspaceId, Map.of()).values());
        }

        @Override
        public void save(UUID workspaceId, Choice choice, Instant at) {
            saved.computeIfAbsent(workspaceId, id -> new java.util.LinkedHashMap<>())
                    .put(choice.provider() + "/" + choice.model().id(), choice);
        }
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
