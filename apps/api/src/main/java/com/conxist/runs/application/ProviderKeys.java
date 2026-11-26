package com.conxist.runs.application;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import com.conxist.platform.CurrentWorkspace;

/**
 * Which key calls a provider for the current workspace: the workspace's own when it connected one, else the key set on
 * this computer ({@code CONXIST_ANTHROPIC_API_KEY}), else none (ADR 0014).
 */
public final class ProviderKeys {

    private final ProviderKeysRepository stored;
    private final Map<String, ApiKey> computerKeys;
    private final CurrentWorkspace workspace;

    /** @param computerKeys the keys set on this computer, by provider; read each time, never changed here */
    public ProviderKeys(ProviderKeysRepository stored, Map<String, ApiKey> computerKeys, CurrentWorkspace workspace) {
        this.stored = stored;
        this.computerKeys = computerKeys;
        this.workspace = workspace;
    }

    /** The key that calls the provider now, if any. */
    Optional<Key> current(String provider) {
        Optional<ProviderKeysRepository.StoredKey> own = stored.find(workspace.id(), provider);
        if (own.isPresent()) {
            return own.map(k -> new Key(k.key(), Source.WORKSPACE, k.addedAt(), k.checkedAt(), k.refused()));
        }
        return Optional.ofNullable(computerKeys.get(provider))
                .map(key -> new Key(key, Source.COMPUTER, null, null, false));
    }

    /** What Settings shows about the key: where it comes from and how its checks went, never the key. */
    Connection connection(String provider) {
        return current(provider).map(k -> new Connection(k.source(), k.source() == Source.WORKSPACE
                        ? k.value().ending() : null, k.addedAt(), k.checkedAt(), k.refused()))
                .orElseGet(() -> new Connection(Source.NONE, null, null, null, false));
    }

    /** Records how a check of the provider went, when the key checked is the workspace's own. */
    void checked(String provider, ApiKey key, Instant at, boolean refused) {
        stored.find(workspace.id(), provider).filter(own -> own.key().equals(key))
                .ifPresent(own -> stored.checked(workspace.id(), provider, at, refused));
    }

    void save(String provider, ApiKey key, Instant at) {
        stored.save(workspace.id(), provider, key, at);
    }

    boolean delete(String provider) {
        return stored.delete(workspace.id(), provider);
    }

    /** Where the key that calls a provider comes from. */
    public enum Source {
        /** The workspace connected its own key in Settings. */
        WORKSPACE,
        /** No key of its own: the one set on this computer. */
        COMPUTER,
        /** No key at all: the provider is not connected. */
        NONE
    }

    /** @param addedAt and {@code checkedAt} are null for the computer's key, which ConXist does not keep */
    record Key(ApiKey value, Source source, Instant addedAt, Instant checkedAt, boolean refused) {
    }

    /**
     * @param keyEnding the last characters of the workspace's own key; null otherwise
     * @param refused   true when the provider refused the key at its last check
     */
    public record Connection(Source source, String keyEnding, Instant addedAt, Instant checkedAt, boolean refused) {
    }
}
