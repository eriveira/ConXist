package com.conxist.capture.application;

import java.time.Instant;
import java.util.UUID;
import com.conxist.capture.domain.ConnectionStatus;
import com.conxist.capture.domain.PlaudAccountConnection;
import com.conxist.capture.domain.PlaudTeamConnection;
import com.conxist.capture.domain.StartFrom;

/**
 * A recorder connection as the web app shows it: a Plaud Team ({@code TEAM}) or a person's own Plaud account
 * ({@code ACCOUNT}). {@code label} is the Team's name or the account's email. Keys, Client IDs and tokens stay inside
 * ConXist.
 */
public record ConnectionView(UUID sourceId, Kind kind, String label, StartFrom startFrom, boolean includeSummaries,
        ConnectionStatus status, Instant lastCheckedAt) {

    public enum Kind { TEAM, ACCOUNT }

    static ConnectionView of(PlaudTeamConnection connection) {
        return new ConnectionView(connection.sourceId(), Kind.TEAM, connection.teamName(), connection.startFrom(),
                connection.includeSummaries(), connection.status(), connection.lastCheckedAt());
    }

    static ConnectionView of(PlaudAccountConnection connection) {
        return new ConnectionView(connection.sourceId(), Kind.ACCOUNT, connection.accountLabel(),
                connection.startFrom(), connection.includeSummaries(), connection.status(),
                connection.lastCheckedAt());
    }
}
