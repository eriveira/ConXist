package com.conxist.capture.application;

import java.net.URI;
import java.time.Duration;
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

    /** What a Plaud file id looks like; anything else from Plaud or a request is refused. */
    String FILE_ID = "[A-Za-z0-9_-]{1,128}";
    /** The most recordings {@link #recentFiles} reads. Plaud's own search reaches the same number. */
    int MAX_RECENT_FILES = 500;

    /** Starts a sign-in: the URL of Plaud's own sign-in page to open in a new window. */
    SignInStart startSignIn(UUID workspaceId);

    /** Plaud sent the person back with a code, or with an error when they cancelled. */
    void finishSignIn(String state, String code, String error);

    Optional<SignIn> signIn(UUID workspaceId, UUID signInId);

    /** Hands a finished sign-in's tokens to a source; the sign-in can no longer be used. */
    void attach(UUID workspaceId, UUID signInId, UUID sourceId);

    /**
     * A connected account's recordings dated from {@code from} to {@code to} (inclusive; {@code to} may be null for no
     * end), newest first, one page at a time from 1.
     */
    FilePage files(UUID sourceId, LocalDate from, LocalDate to, int page);

    /**
     * The newest recordings of a finished sign-in that no source uses yet, at most {@link #MAX_RECENT_FILES},
     * optionally within dates. Only what a list shows: no transcripts are read.
     */
    RecentFiles recentFiles(UUID workspaceId, UUID signInId, DateRange dates);

    /** Empty while Plaud has not finished the transcript. */
    Optional<Transcript> transcript(UUID sourceId, String fileId);

    /** Plaud's notes on the recording (summary, action items), as Markdown. */
    List<String> notes(UUID sourceId, String fileId);

    record SignInStart(UUID signInId, URI authorizationUrl) {
    }

    enum SignInStatus { WAITING, SIGNED_IN, CANCELLED, FAILED }

    /**
     * {@code accountLabel} is for people (the email); {@code accountKey} names the Plaud account and never changes,
     * so it decides whether two sign-ins are the same account. Both are null until signed in.
     */
    record SignIn(UUID id, SignInStatus status, String accountLabel, String accountKey) {
    }

    /** {@code length} is null when Plaud does not say. */
    record AccountFile(String id, String title, Instant recordedAt, Duration length) {

        public AccountFile(String id, String title, Instant recordedAt) {
            this(id, title, recordedAt, null);
        }
    }

    record FilePage(List<AccountFile> files, boolean hasMore) {
    }

    /** Days from {@code from} to {@code to}, inclusive; either may be null for no limit. */
    record DateRange(LocalDate from, LocalDate to) {

        public static final DateRange ANY = new DateRange(null, null);

        public boolean reversed() {
            return from != null && to != null && from.isAfter(to);
        }
    }

    /** {@code capped}: the account has more recordings in the range than were read. */
    record RecentFiles(List<AccountFile> files, boolean capped) {
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
