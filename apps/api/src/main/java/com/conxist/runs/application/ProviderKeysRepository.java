package com.conxist.runs.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Each workspace's own key per LLM provider, stored encrypted (ADR 0004, ADR 0014). */
public interface ProviderKeysRepository {

    Optional<StoredKey> find(UUID workspaceId, String provider);

    /** Adds the workspace's key, or replaces the one it had; a saved key was just checked and works. */
    void save(UUID workspaceId, String provider, ApiKey key, Instant at);

    /** Records a check of the stored key: when it ran, and whether the provider refused the key. */
    void checked(UUID workspaceId, String provider, Instant at, boolean refused);

    /** @return false when the workspace had no key for the provider */
    boolean delete(UUID workspaceId, String provider);

    /** @param refused true when the provider refused the key at its last check */
    record StoredKey(ApiKey key, Instant addedAt, Instant checkedAt, boolean refused) {
    }
}
