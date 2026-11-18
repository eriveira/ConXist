package com.conxist.sources.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import com.conxist.sources.Provider;
import com.conxist.sources.SourceSummary;

/** A provider connected to a workspace's hub. */
public record Source(UUID id, UUID workspaceId, Provider provider, String name, String accountLabel, Instant addedAt) {

    public static final int MAX_NAME_LENGTH = 80;
    public static final int MAX_ACCOUNT_LABEL_LENGTH = 120;

    /** Builds a new source, enforcing the rules every source must meet. */
    public static Source add(UUID id, UUID workspaceId, SourceDraft draft, Instant now) {
        Provider provider = requireAddable(draft.provider());
        String name = requireName(draft.name());
        String accountLabel = requireAccount(provider, draft.accountLabel());
        return new Source(id, workspaceId, provider, name, accountLabel, now);
    }

    public SourceSummary toSummary() {
        return new SourceSummary(id, provider.category(), provider, name, accountLabel, addedAt);
    }

    private static Provider requireAddable(Provider provider) {
        if (provider == null) {
            throw new InvalidSource("provider-required", "Choose what to connect.");
        }
        if (!provider.canBeAdded()) {
            throw new InvalidSource("coming-soon", provider.displayName() + " is coming soon.");
        }
        return provider;
    }

    private static String requireName(String name) {
        String trimmed = Optional.ofNullable(name).map(String::strip).orElse("");
        if (trimmed.isEmpty()) {
            throw new InvalidSource("name-required", "Give the source a name.");
        }
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new InvalidSource("name-too-long", "Keep the name under " + MAX_NAME_LENGTH + " characters.");
        }
        return trimmed;
    }

    private static String requireAccount(Provider provider, String accountLabel) {
        String trimmed = Optional.ofNullable(accountLabel).map(String::strip).orElse("");
        if (provider.signInRequired() && trimmed.isEmpty()) {
            throw new InvalidSource("sign-in-required", "Sign in to " + provider.displayName() + " first.");
        }
        if (trimmed.length() > MAX_ACCOUNT_LABEL_LENGTH) {
            throw new InvalidSource("account-too-long", "The account name is too long.");
        }
        return trimmed;
    }
}
