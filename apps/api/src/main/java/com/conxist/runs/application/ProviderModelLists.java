package com.conxist.runs.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The models each provider offers, kept for a while so Settings does not ask the provider on every visit (ADR 0014).
 * A refresh asks again only once the kept list is a little old, and one provider is asked once at a time.
 * The list is the same for every workspace: it depends on the API's key, not on the workspace.
 */
public final class ProviderModelLists {

    private static final Logger log = LoggerFactory.getLogger(ProviderModelLists.class);
    private final Duration keepFor;
    private final Duration minRefresh;
    /** One fetch per provider at a time: callers that arrive meanwhile wait, then get what it fetched. */
    private final Map<String, Object> locks = new ConcurrentHashMap<>();
    private final Duration timeout;
    private final Clock clock;
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
     * The provider's models: the kept list while it is fresh, else asked again. When asking fails, the last list it
     * gave comes back with the reason, or no models when it never answered.
     *
     * @param refresh ask the provider now, unless the kept list is newer than the refresh minimum
     */
    Listing models(ModelPort port, boolean refresh) {
        synchronized (locks.computeIfAbsent(port.provider(), provider -> new Object())) {
            return fetch(port, refresh);
        }
    }

    private Listing fetch(ModelPort port, boolean refresh) {
        Instant now = clock.instant();
        Fetched kept = lists.get(port.provider());
        if (kept != null && kept.at().plus(refresh ? minRefresh : keepFor).isAfter(now)) {
            return new Listing(kept.models(), null);
        }
        try {
            List<ProviderModel> models = port.models(timeout);
            lists.put(port.provider(), new Fetched(models, now));
            return new Listing(models, null);
        } catch (ModelPort.Failure failure) {
            log.warn("Could not list {} models: {} ({})", port.provider(), failure.reason(),
                    failure.getCause() == null ? "no cause" : failure.getCause().getClass().getSimpleName());
            return new Listing(kept == null ? List.of() : kept.models(), switch (failure.reason()) {
                case KEY_REJECTED -> ListError.KEY_REJECTED;
                case BUSY -> ListError.BUSY;
                case FAILED -> ListError.FAILED;
            });
        } catch (ModelPort.Timeout late) {
            log.warn("Could not list {} models: no answer in {}", port.provider(), timeout);
            return new Listing(kept == null ? List.of() : kept.models(), ListError.TIMEOUT);
        }
    }

    /** @param error why the list could not be fetched now; null when it was */
    record Listing(List<ProviderModel> models, ListError error) {
    }

    /** Why a provider's list could not be fetched, as a stable code the app words in the person's language. */
    public enum ListError {
        KEY_REJECTED, BUSY, TIMEOUT, FAILED
    }

    private record Fetched(List<ProviderModel> models, Instant at) {
    }
}
