package com.conxist.capture.application;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.conxist.capture.application.PlaudTeam.Transcript;

/**
 * A person's own Plaud account, reached through Plaud's sign-in page (OAuth) and Plaud's MCP server. Read-only.
 * The tokens Plaud issues never leave the adapter: a sign-in is referred to by its id, and a connected account by its
 * source id. {@link AccessDenied} means Plaud no longer accepts the sign-in (the person must sign in again);
 * {@link Unavailable} is anything that may pass.
 */
public interface PlaudAccounts {

    /** Starts a sign-in: the URL of Plaud's own sign-in page to open in a new window. */
    SignInStart startSignIn(UUID workspaceId);

    /** Plaud sent the person back with a code, or with an error when they cancelled. */
    void finishSignIn(String state, String code, String error);

    Optional<SignIn> signIn(UUID workspaceId, UUID signInId);

    /** Hands a finished sign-in's tokens to a source; the sign-in can no longer be used. */
    void attach(UUID workspaceId, UUID signInId, UUID sourceId);

    /** Recordings with their date on or after {@code from}, newest first, one page at a time from 1. */
    FilePage files(UUID sourceId, LocalDate from, int page);

    /** Empty while Plaud has not finished the transcript. */
    Optional<Transcript> transcript(UUID sourceId, String fileId);

    /** Plaud's notes on the recording (summary, action items), as Markdown. */
    List<String> notes(UUID sourceId, String fileId);

    record SignInStart(UUID signInId, URI authorizationUrl) {
    }

    enum SignInStatus { WAITING, SIGNED_IN, CANCELLED, FAILED }

    record SignIn(UUID id, SignInStatus status, String accountLabel) {
    }

    record AccountFile(String id, String title, Instant recordedAt) {
    }

    record FilePage(List<AccountFile> files, boolean hasMore) {
    }

    class AccessDenied extends RuntimeException {
        public AccessDenied(String reason) {
            super(reason);
        }
    }

    class Unavailable extends RuntimeException {
        public Unavailable(String reason) {
            super(reason);
        }
    }
}
