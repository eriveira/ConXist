package com.conxist.capture.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A recorder source's link to one person's own Plaud account. {@code readThrough} is when the last complete sync
 * started; the next sync looks back a little before it, so recordings Plaud was still transcribing are picked up.
 */
public record PlaudAccountConnection(
        UUID sourceId,
        UUID workspaceId,
        String accountLabel,
        StartFrom startFrom,
        Instant cutoff,
        boolean includeSummaries,
        Instant readThrough,
        ConnectionStatus status,
        Instant lastCheckedAt,
        Instant connectedAt) {

    /** How far before the last sync to look again for recordings whose transcript was not ready. */
    public static final Duration LOOK_BACK = Duration.ofDays(3);
    public static final int MAX_LABEL_LENGTH = 200;

    public PlaudAccountConnection {
        Objects.requireNonNull(sourceId);
        Objects.requireNonNull(workspaceId);
        Objects.requireNonNull(startFrom);
        Objects.requireNonNull(cutoff);
        Objects.requireNonNull(status);
        Objects.requireNonNull(connectedAt);
        accountLabel = accountLabel == null ? "" : accountLabel.strip();
        if (accountLabel.length() > MAX_LABEL_LENGTH) {
            accountLabel = accountLabel.substring(0, MAX_LABEL_LENGTH);
        }
    }

    public static PlaudAccountConnection connect(UUID sourceId, UUID workspaceId, String accountLabel,
            StartFrom startFrom, boolean includeSummaries, Instant now) {
        return new PlaudAccountConnection(sourceId, workspaceId, accountLabel, startFrom, startFrom.cutoff(now),
                includeSummaries, null, ConnectionStatus.ACTIVE, null, now);
    }

    /** The earliest recording time the next sync must look at. */
    public Instant readFrom() {
        if (readThrough == null) {
            return cutoff;
        }
        Instant lookBack = readThrough.minus(LOOK_BACK);
        return lookBack.isAfter(cutoff) ? lookBack : cutoff;
    }

    public boolean wasRecordedInRange(Instant recordedAt) {
        return recordedAt != null && !recordedAt.isBefore(cutoff);
    }

    public PlaudAccountConnection checked(Instant startedAt, Instant at) {
        return new PlaudAccountConnection(sourceId, workspaceId, accountLabel, startFrom, cutoff, includeSummaries,
                startedAt, ConnectionStatus.ACTIVE, at, connectedAt);
    }

    public PlaudAccountConnection paused(Instant at) {
        return new PlaudAccountConnection(sourceId, workspaceId, accountLabel, startFrom, cutoff, includeSummaries,
                readThrough, ConnectionStatus.PAUSED, at, connectedAt);
    }
}
