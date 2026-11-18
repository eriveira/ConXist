package com.conxist.capture.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import com.conxist.capture.domain.MeetingText.Line;

/**
 * Plaud's Enterprise API for one Team workspace, read-only. Every call signs in with the ConXist key registered for
 * the credential's Client ID. Implementations throw {@link AccessDenied} when Plaud refuses the credential,
 * {@link CheckpointExpired} when the change feed must be read from the start, and {@link Unavailable} for anything
 * worth retrying later.
 */
public interface PlaudTeam {

    /** The Team workspace the credential reads. Signs in, so it doubles as a connection test. */
    String teamName(Credential credential);

    /** One page of the change feed, oldest change first. A null checkpoint reads the feed from the start. */
    ChangePage changes(Credential credential, String checkpoint, String cursor);

    /** Empty when the recording was deleted in Plaud after it appeared in the feed. */
    Optional<Transcript> transcript(Credential credential, String fileId);

    List<String> summaries(Credential credential, String fileId);

    record Credential(String clientId, String keyId) {
    }

    record ChangePage(List<TeamFile> files, String nextCursor, String nextCheckpoint, boolean hasMore) {
    }

    record TeamFile(String id, String title, boolean deleted, Instant recordedAt, boolean hasTranscript,
            boolean hasSummary) {
    }

    record Transcript(String language, List<Line> lines) {
    }

    class AccessDenied extends RuntimeException {
        public AccessDenied(String reason) {
            super(reason);
        }
    }

    class CheckpointExpired extends RuntimeException {
        public CheckpointExpired() {
            super("checkpoint expired");
        }
    }

    class Unavailable extends RuntimeException {
        public Unavailable(String reason) {
            super(reason);
        }
    }
}
