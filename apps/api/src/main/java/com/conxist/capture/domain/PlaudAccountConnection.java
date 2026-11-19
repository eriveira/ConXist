package com.conxist.capture.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A recorder source's link to one person's own Plaud account. {@code readThrough} is when the last complete sync
 * started; the next sync looks back a little before it, so recordings Plaud was still transcribing are picked up.
 * {@code importNew} says whether recordings made after connecting come in on their own; older ones come in only when
 * the person picked them.
 */
public record PlaudAccountConnection(
        UUID sourceId,
        UUID workspaceId,
        String accountLabel,
        String accountKey,
        StartFrom startFrom,
        Instant cutoff,
        boolean includeSummaries,
        boolean importNew,
        Instant readThrough,
        ConnectionStatus status,
        Instant lastCheckedAt,
        Instant connectedAt) {

    /**
     * How far before the last sync to look again for recordings whose transcript was not ready. Long, because a
     * recorder may upload days after the meeting and personal accounts often start transcription by hand.
     */
    public static final Duration LOOK_BACK = Duration.ofDays(30);
    public static final int MAX_LABEL_LENGTH = 200;

    public PlaudAccountConnection {
        Objects.requireNonNull(sourceId);
        Objects.requireNonNull(workspaceId);
        Objects.requireNonNull(accountKey);
        Objects.requireNonNull(startFrom);
        Objects.requireNonNull(cutoff);
        Objects.requireNonNull(status);
        Objects.requireNonNull(connectedAt);
        accountLabel = accountLabel == null ? "" : accountLabel.strip();
        if (accountLabel.length() > MAX_LABEL_LENGTH) {
            accountLabel = accountLabel.substring(0, MAX_LABEL_LENGTH);
        }
    }

    /** New recordings are those made from {@code now} on; anything older is imported only if picked. */
    public static PlaudAccountConnection connect(UUID sourceId, UUID workspaceId, String accountLabel,
            String accountKey, boolean importNew, boolean includeSummaries, Instant now) {
        return new PlaudAccountConnection(sourceId, workspaceId, accountLabel, accountKey, StartFrom.NEW_ONLY,
                StartFrom.NEW_ONLY.cutoff(now), includeSummaries, importNew, null, ConnectionStatus.ACTIVE, null, now);
    }

    /** The earliest recording time the next sync must look at. */
    public Instant readFrom() {
        if (readThrough == null) {
            return cutoff;
        }
        Instant lookBack = readThrough.minus(LOOK_BACK);
        return lookBack.isAfter(cutoff) ? lookBack : cutoff;
    }

    /** One source holds one person's meetings: a sign-in to another account must not take it over. */
    public boolean isAccount(String key) {
        return accountKey.equals(key);
    }

    public boolean wasRecordedInRange(Instant recordedAt) {
        return recordedAt != null && !recordedAt.isBefore(cutoff);
    }

    public PlaudAccountConnection checked(Instant startedAt, Instant at) {
        return new PlaudAccountConnection(sourceId, workspaceId, accountLabel, accountKey, startFrom, cutoff, includeSummaries,
                importNew, startedAt, ConnectionStatus.ACTIVE, at, connectedAt);
    }

    public PlaudAccountConnection paused(Instant at) {
        return new PlaudAccountConnection(sourceId, workspaceId, accountLabel, accountKey, startFrom, cutoff, includeSummaries,
                importNew, readThrough, ConnectionStatus.PAUSED, at, connectedAt);
    }
}
