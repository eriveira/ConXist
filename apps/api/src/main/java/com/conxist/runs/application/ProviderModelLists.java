package com.conxist.runs.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The models each provider offers a key, kept for a while so Settings does not ask the provider on every visit
 * (ADR 0014). A refresh asks again only once the kept list is a little old, and one key is asked once at a time.
 * Lists are kept per key, since what a provider offers depends on the key; workspaces that share a key share its list.
 */
public final class ProviderModelLists {

    private static final Logger log = LoggerFactory.getLogger(ProviderModelLists.class);
    private final Duration keepFor;
    private final Duration minRefresh;
    /** One fetch per key at a time: callers that arrive meanwhile wait, then get what it fetched. */
    private final Map<String, Object> locks = new ConcurrentHashMap<>();
    private final Duration timeout;
    private final Clock clock;
    /** By provider and a digest of the key, so the map never holds a key. */
    private final Map<String, Fetched> lists = new ConcurrentHashMap<>();

    /**
     * @param minRefresh how old the kept list must be before a refresh asks the provider again, so repeated refreshes
     *                   don't use up the key's rate limit
     */
    public ProviderModelLists(Duration keepFor, Duration minRefresh, Duration timeout, Clock clock) {
        this.keepFor = keepFor;
        this.minRefresh = minRefresh;
        this.timeout = timeout;
        this.clock = clock;
    }

    /**
     * The models the provider offers the key: the kept list while it is fresh, else asked again. When asking fails,
     * the last list it gave comes back with the reason, or no models when it never answered.
     *
     * @param refresh ask the provider now, unless the kept list is newer than the refresh minimum
     */
    Listing models(ModelPort port, ApiKey key, boolean refresh) {
        String id = id(port, key);
        synchronized (locks.computeIfAbsent(id, any -> new Object())) {
            Fetched kept = lists.get(id);
            if (kept != null && kept.at().plus(refresh ? minRefresh : keepFor).isAfter(clock.instant())) {
                return new Listing(kept.models(), null, false);
            }
            return fetch(port, key, id, kept);
        }
    }

    /**
     * Asks the provider now, whatever is kept: how ConXist checks a key that was just pasted or that a person tests.
     * Checks wait for each other, so one key is never asked twice at once.
     */
    Listing check(ModelPort port, ApiKey key) {
        String id = id(port, key);
        synchronized (locks.computeIfAbsent(id, any -> new Object())) {
            return fetch(port, key, id, lists.get(id));
        }
    }

    private Listing fetch(ModelPort port, ApiKey key, String id, Fetched kept) {
        List<ProviderModel> before = kept == null ? List.of() : kept.models();
        try {
            List<ProviderModel> models = port.models(key, timeout);
            lists.put(id, new Fetched(models, clock.instant()));
            return new Listing(models, null, true);
        } catch (ModelPort.Failure failure) {
            log.warn("Could not list {} models: {} ({})", port.provider(), failure.reason(),
                    failure.getCause() == null ? "no cause" : failure.getCause().getClass().getSimpleName());
            if (failure.reason() == ModelPort.Failure.Reason.KEY_REJECTED) {
                lists.remove(id);
                before = List.of();
            }
            return new Listing(before, switch (failure.reason()) {
                case KEY_REJECTED -> ListError.KEY_REJECTED;
                case BUSY -> ListError.BUSY;
                case FAILED -> ListError.FAILED;
            }, true);
        } catch (ModelPort.Timeout late) {
            log.warn("Could not list {} models: no answer in {}", port.provider(), timeout);
            return new Listing(before, ListError.TIMEOUT, true);
        }
    }

    private static String id(ModelPort port, ApiKey key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.value().getBytes(StandardCharsets.UTF_8));
            return port.provider() + "/" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException("SHA-256 is not available", missing);
        }
    }

    /**
     * @param error why the list could not be fetched now; null when it was
     * @param asked true when the provider was asked just now, false when the kept list came back
     */
    record Listing(List<ProviderModel> models, ListError error, boolean asked) {

        /** The provider answered about the key: it listed its models or refused the key. */
        boolean answered() {
            return asked && (error == null || error == ListError.KEY_REJECTED);
        }
    }

    /** Why a provider's list could not be fetched, as a stable code the app words in the person's language. */
    public enum ListError {
        KEY_REJECTED, BUSY, TIMEOUT, FAILED
    }

    private record Fetched(List<ProviderModel> models, Instant at) {
    }
}
