package com.conxist.connectors.application;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Google sign-ins being made for Gmail (ADR 0021): from Sign in with Google until the integration is added or signs in
 * again. They live in memory for {@link #KEPT_FOR} only and are lost on restart; the person signs in again. The
 * refresh token here is in the API's memory only, never stored, until the integration is added.
 */
public final class GmailSignIns {

    public static final Duration KEPT_FOR = Duration.ofMinutes(15);
    static final int MAX_KEPT = 200;

    private final Map<UUID, SignIn> signIns = new ConcurrentHashMap<>();
    private final Clock clock;

    public GmailSignIns(Clock clock) {
        this.clock = clock;
    }

    public enum Status {
        /** Waiting for the person on Google's page. */
        SIGNING_IN,
        /** Google answered: the person picks the labels and tools. */
        READY,
        /** The sign-in did not finish; {@code error} says why. */
        FAILED
    }

    /**
     * @param integrationId the Gmail integration that signs in again, or null for a new one
     * @param credential    the refresh token, once {@link Status#READY}
     * @param labels        the mailbox's own labels, once {@link Status#READY}
     * @param error         a code such as {@code sign-in-refused}, for {@link Status#FAILED}
     */
    public record SignIn(UUID id, UUID workspaceId, UUID integrationId, Status status, String state, String verifier,
            URI url, String credential, String account, List<String> labels, String error, Instant startedAt) {

        SignIn ready(String newCredential, IntegrationApi.Account account) {
            return new SignIn(id, workspaceId, integrationId, Status.READY, null, null, null, newCredential,
                    account.login(), account.repositories(), null, startedAt);
        }

        SignIn relisted(IntegrationApi.Account account) {
            return new SignIn(id, workspaceId, integrationId, status, null, null, null, credential, account.login(),
                    account.repositories(), null, startedAt);
        }

        SignIn claimed() {
            return new SignIn(id, workspaceId, integrationId, status, null, verifier, url, null, null, null, null,
                    startedAt);
        }

        SignIn failed(String code) {
            return new SignIn(id, workspaceId, integrationId, Status.FAILED, null, null, null, null, null, null, code,
                    startedAt);
        }

        @Override
        public String toString() {
            return "SignIn[" + id + ", " + status + "]";
        }
    }

    SignIn save(SignIn signIn) {
        forgetOld();
        signIns.put(signIn.id(), signIn);
        return signIn;
    }

    Optional<SignIn> find(UUID workspaceId, UUID id) {
        return Optional.ofNullable(signIns.get(id)).filter(this::fresh)
                .filter(signIn -> signIn.workspaceId().equals(workspaceId));
    }

    /** Takes the sign-in waiting for this OAuth state, once: a second callback with it finds nothing. */
    Optional<SignIn> claim(String state) {
        if (state == null || state.isEmpty()) {
            return Optional.empty();
        }
        return signIns.values().stream().filter(this::fresh)
                .filter(signIn -> signIn.status() == Status.SIGNING_IN && state.equals(signIn.state()))
                .findFirst()
                .flatMap(waiting -> {
                    SignIn claimed = waiting.claimed();
                    return signIns.replace(waiting.id(), waiting, claimed) ? Optional.of(claimed) : Optional.empty();
                });
    }

    void forget(UUID id) {
        signIns.remove(id);
    }

    Instant now() {
        return clock.instant();
    }

    private boolean fresh(SignIn signIn) {
        return signIn.startedAt().plus(KEPT_FOR).isAfter(clock.instant());
    }

    private void forgetOld() {
        signIns.values().removeIf(signIn -> !fresh(signIn));
        while (signIns.size() >= MAX_KEPT) {
            signIns.values().stream().min(Comparator.comparing(SignIn::startedAt))
                    .ifPresent(oldest -> signIns.remove(oldest.id()));
        }
    }
}
