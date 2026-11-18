package com.conxist.capture.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A recorder source's link to one Plaud Team workspace. ConXist signs in with its own key ({@code keyId}), which the
 * Team's admin registered with Plaud for {@code clientId}. {@code checkpoint} marks how far Plaud's change feed has
 * been read; it is empty until the first complete sync.
 */
public record PlaudTeamConnection(
        UUID sourceId,
        UUID workspaceId,
        String clientId,
        String keyId,
        String teamName,
        StartFrom startFrom,
        Instant cutoff,
        boolean includeSummaries,
        String checkpoint,
        ConnectionStatus status,
        Instant lastCheckedAt,
        Instant connectedAt) {

    public static final int MAX_CLIENT_ID_LENGTH = 200;
    public static final int MAX_TEAM_NAME_LENGTH = 200;

    public PlaudTeamConnection {
        Objects.requireNonNull(sourceId);
        Objects.requireNonNull(workspaceId);
        Objects.requireNonNull(keyId);
        Objects.requireNonNull(teamName);
        Objects.requireNonNull(startFrom);
        Objects.requireNonNull(cutoff);
        Objects.requireNonNull(status);
        Objects.requireNonNull(connectedAt);
        clientId = cleanClientId(clientId);
        teamName = teamName.strip().length() > MAX_TEAM_NAME_LENGTH
                ? teamName.strip().substring(0, MAX_TEAM_NAME_LENGTH) : teamName.strip();
    }

    public static PlaudTeamConnection connect(UUID sourceId, UUID workspaceId, String clientId, String keyId,
            String teamName, StartFrom startFrom, boolean includeSummaries, Instant now) {
        return new PlaudTeamConnection(sourceId, workspaceId, clientId, keyId, teamName, startFrom,
                startFrom.cutoff(now), includeSummaries, null, ConnectionStatus.ACTIVE, null, now);
    }

    /** Trims what a person pasted and rejects what cannot be a Client ID, before anything is sent to Plaud. */
    public static String cleanClientId(String clientId) {
        String trimmed = clientId == null ? "" : clientId.strip();
        if (trimmed.isEmpty() || trimmed.length() > MAX_CLIENT_ID_LENGTH || !trimmed.matches("[A-Za-z0-9._:-]+")) {
            throw new InvalidRecorderLink("client-id-invalid", "Paste the Client ID exactly as your Plaud admin sent it.");
        }
        return trimmed;
    }

    public boolean wasRecordedInRange(Instant recordedAt) {
        return recordedAt != null && !recordedAt.isBefore(cutoff);
    }

    /** A complete pass over the change feed: remember where it ended. */
    public PlaudTeamConnection checked(String nextCheckpoint, Instant at) {
        return new PlaudTeamConnection(sourceId, workspaceId, clientId, keyId, teamName, startFrom, cutoff,
                includeSummaries, nextCheckpoint, ConnectionStatus.ACTIVE, at, connectedAt);
    }

    /** Plaud no longer knows the checkpoint: read the whole feed again. Imported meetings are skipped by id. */
    public PlaudTeamConnection withoutCheckpoint() {
        return new PlaudTeamConnection(sourceId, workspaceId, clientId, keyId, teamName, startFrom, cutoff,
                includeSummaries, null, status, lastCheckedAt, connectedAt);
    }

    public PlaudTeamConnection paused(Instant at) {
        return new PlaudTeamConnection(sourceId, workspaceId, clientId, keyId, teamName, startFrom, cutoff,
                includeSummaries, checkpoint, ConnectionStatus.PAUSED, at, connectedAt);
    }
}
