package com.conxist.runs.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Each workspace's own key per LLM provider, stored encrypted (ADR 0004, ADR 0014). */
public interface ProviderKeysRepository {

    /** What Settings shows about the stored key; the key itself stays sealed. */
    Optional<KeyStatus> status(UUID workspaceId, String provider);

    /** The stored key, opened to call the provider. */
    Optional<ApiKey> open(UUID workspaceId, String provider);

    /** Adds the workspace's key, or replaces the one it had; a saved key was just checked and works. */
    void save(UUID workspaceId, String provider, ApiKey key, Instant at);

    /** Records a check of the stored key: when it ran, and whether the provider refused the key. */
    void checked(UUID workspaceId, String provider, Instant at, boolean refused);

    /** @return false when the workspace had no key for the provider */
    boolean delete(UUID workspaceId, String provider);

    /**
     * @param ending  the key's last characters, kept in the clear to show which key it is
     * @param refused true when the provider refused the key at its last check
     */
    record KeyStatus(String ending, Instant addedAt, Instant checkedAt, boolean refused) {
    }
}
