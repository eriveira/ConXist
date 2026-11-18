package com.conxist.capture.application;

import java.time.Instant;
import java.util.UUID;
import com.conxist.capture.domain.ConnectionStatus;
import com.conxist.capture.domain.PlaudTeamConnection;
import com.conxist.capture.domain.StartFrom;

/** A recorder connection as the web app shows it. The Client ID and key stay inside ConXist. */
public record ConnectionView(UUID sourceId, String teamName, StartFrom startFrom, boolean includeSummaries,
        ConnectionStatus status, Instant lastCheckedAt) {

    static ConnectionView of(PlaudTeamConnection connection) {
        return new ConnectionView(connection.sourceId(), connection.teamName(), connection.startFrom(),
                connection.includeSummaries(), connection.status(), connection.lastCheckedAt());
    }
}
