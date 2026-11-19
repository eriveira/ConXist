package com.conxist.capture.adapter.plaudaccount;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.conxist.capture.adapter.plaudaccount.PlaudOAuth.Tokens;
import com.conxist.capture.application.PlaudAccounts.AccessDenied;
import com.conxist.capture.application.PlaudAccounts.Unavailable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Plaud MCP tool calls made with stored tokens: a connected source's, or a finished sign-in's that no source uses yet.
 * Tokens are refreshed before they expire and once more when Plaud refuses them; a refused refresh means signed out.
 */
final class PlaudTokenSlots {

    private static final Duration REFRESH_MARGIN = Duration.ofSeconds(60);
    private static final Logger log = LoggerFactory.getLogger(PlaudTokenSlots.class);

    private final PlaudOAuth oauth;
    private final McpClient mcp;
    private final PlaudAccountStore store;
    private final Clock clock;
    private final Duration signedInTime;
    /** One refresh at a time per source or sign-in: Plaud rotates refresh tokens, so two at once would sign out. */
    private final Map<UUID, Object> refreshLocks = new ConcurrentHashMap<>();

    PlaudTokenSlots(PlaudOAuth oauth, McpClient mcp, PlaudAccountStore store, Clock clock, Duration signedInTime) {
        this.oauth = oauth;
        this.mcp = mcp;
        this.store = store;
        this.clock = clock;
        this.signedInTime = signedInTime;
    }

    /** Where a set of tokens lives: a connected source, or a finished sign-in no source uses yet. */
    interface Slot {
        Optional<Tokens> load();

        void replace(Tokens fresh);

        /** Refreshes of one slot happen one at a time. */
        UUID key();
    }

    Slot source(UUID sourceId) {
        return new Slot() {
            @Override
            public Optional<Tokens> load() {
                return store.tokens(sourceId);
            }

            @Override
            public void replace(Tokens fresh) {
                store.replaceTokens(sourceId, fresh, clock.instant());
            }

            @Override
            public UUID key() {
                return sourceId;
            }
        };
    }

    Slot signIn(UUID workspaceId, UUID signInId) {
        return new Slot() {
            @Override
            public Optional<Tokens> load() {
                return store.signInTokens(workspaceId, signInId, clock.instant().minus(signedInTime));
            }

            @Override
            public void replace(Tokens fresh) {
                if (!store.replaceSignInTokens(signInId, fresh)) {
                    throw new AccessDenied("sign-in used or expired during a refresh");
                }
            }

            @Override
            public UUID key() {
                return signInId;
            }
        };
    }

    /**
     * Runs {@code work} while no refresh of {@code key}'s tokens can run, such as handing a sign-in's tokens to a
     * source: a refresh that finished meanwhile would otherwise be lost, and the source left with a spent token.
     */
    <T> T exclusively(UUID key, java.util.function.Supplier<T> work) {
        synchronized (refreshLocks.computeIfAbsent(key, id -> new Object())) {
            return work.get();
        }
    }

    /** One tool call with a slot's tokens: refreshed before expiry, and once more if Plaud refuses them. */
    String call(Slot slot, String tool, Map<String, Object> arguments) {
        Tokens tokens = slot.load().orElseThrow(() -> new AccessDenied("no tokens"));
        try {
            if (tokens.expiresAt().isBefore(clock.instant().plus(REFRESH_MARGIN))) {
                tokens = refresh(slot, tokens);
            }
            try {
                return mcp.callTool(tokens.accessToken(), tool, arguments);
            } catch (McpClient.TokenRefused refused) {
                mcp.forget(tokens.accessToken());
                Tokens fresh = refresh(slot, tokens);
                try {
                    return mcp.callTool(fresh.accessToken(), tool, arguments);
                } catch (McpClient.TokenRefused again) {
                    throw new AccessDenied("token refused after refresh");
                }
            }
        } catch (PlaudOAuth.SignedOut signedOut) {
            throw new AccessDenied(signedOut.getMessage());
        } catch (PlaudOAuth.Unreachable | McpClient.SessionGone unreachable) {
            throw new Unavailable(unreachable.getClass().getSimpleName());
        }
    }

    /**
     * Swaps {@code used} for new tokens, one refresh per slot at a time. If another call already refreshed them, the
     * stored tokens are newer than {@code used} and are taken as they are.
     */
    private Tokens refresh(Slot slot, Tokens used) {
        synchronized (refreshLocks.computeIfAbsent(slot.key(), id -> new Object())) {
            Tokens stored = slot.load().orElseThrow(() -> new AccessDenied("no tokens"));
            if (!stored.accessToken().equals(used.accessToken())) {
                return stored;
            }
            Tokens fresh = oauth.refresh(used);
            slot.replace(fresh);
            mcp.forget(used.accessToken());
            log.info("Refreshed Plaud tokens ({})", slot.key());
            return fresh;
        }
    }
}
