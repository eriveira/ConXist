package com.conxist.runs.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import com.conxist.platform.RuleViolation;

/**
 * Connects a workspace to an LLM provider with its own key, tests the connection, and disconnects it (ADR 0014). A key
 * is saved only once the provider has accepted it, and only encrypted; it never comes back out.
 */
public final class ConnectProvider {

    private final LlmProviders providers;
    private final ProviderModelLists lists;
    private final ProviderKeys keys;
    private final Clock clock;

    public ConnectProvider(LlmProviders providers, ProviderModelLists lists, ProviderKeys keys, Clock clock) {
        this.providers = providers;
        this.lists = lists;
        this.keys = keys;
        this.clock = clock;
    }

    /** Checks the pasted key with the provider, then saves it as the workspace's own, replacing any it had. */
    public LlmProviders.ProviderView connect(String provider, String pasted) {
        ModelPort port = providers.port(provider).orElseThrow(LlmProviders::unknownProvider);
        ApiKey key = ApiKey.pasted(pasted).orElseThrow(() -> new RuleViolation("provider.key-invalid",
                "This does not look like an API key. Copy it again from the provider's console."));
        ProviderModelLists.Listing listing = lists.check(port, key);
        if (listing.error() != null) {
            throw problem(listing.error());
        }
        keys.save(provider, key, clock.instant());
        return providers.providerView(provider);
    }

    /** Asks the provider now with the workspace's key and says how it went; the answer is also kept for Settings. */
    public Check test(String provider) {
        ModelPort port = providers.port(provider).orElseThrow(LlmProviders::unknownProvider);
        ApiKey key = providers.key(provider)
                .orElseThrow(() -> new RuleViolation("provider.not-connected", "Connect the provider first."));
        Instant asked = clock.instant();
        ProviderModelLists.Listing listing = lists.check(port, key);
        long millis = Duration.between(asked, clock.instant()).toMillis();
        providers.recordCheck(provider, key, listing);
        return new Check(listing.error() == null, listing.error(), millis, providers.providerView(provider));
    }

    /**
     * Forgets the workspace's own key. Its agents then run on this computer's key when one is set, else they can't
     * run until a key is connected again.
     */
    public LlmProviders.View disconnect(String provider) {
        providers.port(provider).orElseThrow(LlmProviders::unknownProvider);
        keys.delete(provider);
        return providers.view();
    }

    private static RuleViolation problem(ProviderModelLists.ListError error) {
        return switch (error) {
            case KEY_REJECTED -> new RuleViolation("provider.key-refused",
                    "The provider refused this key. Check that it is active and copied in full.");
            case BUSY -> new RuleViolation("provider.busy", "The provider is busy right now. Try again in a minute.");
            case TIMEOUT -> new RuleViolation("provider.timeout", "The provider did not answer in time. Try again.");
            case FAILED -> new RuleViolation("provider.unreachable",
                    "ConXist could not reach the provider. Check the connection and try again.");
        };
    }

    /**
     * @param works  true when the provider answered with the key
     * @param error  why it did not; null when it did
     * @param millis how long the provider took to answer
     */
    public record Check(boolean works, ProviderModelLists.ListError error, long millis,
            LlmProviders.ProviderView provider) {
    }
}
