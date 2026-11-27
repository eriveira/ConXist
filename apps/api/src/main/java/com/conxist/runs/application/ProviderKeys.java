package com.conxist.runs.application;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import com.conxist.platform.CurrentWorkspace;

/**
 * Which key calls a provider for the current workspace: the workspace's own when it connected one, else the key set on
 * this computer ({@code CONXIST_ANTHROPIC_API_KEY} or {@code CONXIST_OPENAI_API_KEY}), else none (ADR 0014). A stored key is opened only to call the
 * provider; Settings reads its status without opening it.
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

    /** The key that calls the provider now, if any, opened. */
    Optional<Key> current(String provider) {
        Optional<ProviderKeysRepository.KeyStatus> own = stored.status(workspace.id(), provider);
        if (own.isPresent()) {
            return stored.open(workspace.id(), provider)
                    .map(key -> new Key(key, Source.WORKSPACE, own.get().refused()));
        }
        return Optional.ofNullable(computerKeys.get(provider)).map(key -> new Key(key, Source.COMPUTER, false));
    }

    /** What Settings shows about the key: where it comes from and how its checks went, never the key. */
    Connection connection(String provider) {
        Optional<ProviderKeysRepository.KeyStatus> own = stored.status(workspace.id(), provider);
        if (own.isPresent()) {
            ProviderKeysRepository.KeyStatus status = own.get();
            return new Connection(Source.WORKSPACE, status.ending(), status.addedAt(), status.checkedAt(),
                    status.refused());
        }
        Source source = computerKeys.containsKey(provider) ? Source.COMPUTER : Source.NONE;
        return new Connection(source, null, null, null, false);
    }

    /** Records how a check went, when the key checked is the workspace's own; nothing is kept about this computer's. */
    void checked(String provider, Key key, Instant at, boolean refused) {
        if (key.source() == Source.WORKSPACE) {
            stored.checked(workspace.id(), provider, at, refused);
        }
    }

    boolean hasOwn(String provider) {
        return stored.status(workspace.id(), provider).isPresent();
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

    /** @param refused true when the provider refused this workspace key at its last check */
    record Key(ApiKey value, Source source, boolean refused) {
    }

    /**
     * @param keyEnding the last characters of the workspace's own key; null otherwise
     * @param addedAt   and {@code checkedAt} are null for this computer's key, which ConXist does not keep
     * @param refused   true when the provider refused the key at its last check
     */
    public record Connection(Source source, String keyEnding, Instant addedAt, Instant checkedAt, boolean refused) {

        boolean connected() {
            return source != Source.NONE;
        }
    }
}
