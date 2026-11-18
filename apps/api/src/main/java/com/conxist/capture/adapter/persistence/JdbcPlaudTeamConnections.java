package com.conxist.capture.adapter.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.conxist.capture.application.PlaudTeamConnections;
import com.conxist.capture.domain.ConnectionStatus;
import com.conxist.capture.domain.PlaudTeamConnection;
import com.conxist.capture.domain.StartFrom;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcPlaudTeamConnections implements PlaudTeamConnections {

    private static final String COLUMNS = """
            source_id, workspace_id, client_id, key_id, team_name, start_from, start_cutoff, include_summaries,
            feed_checkpoint, status, last_checked_at, connected_at""";

    private final JdbcClient jdbc;

    JdbcPlaudTeamConnections(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(PlaudTeamConnection c) {
        jdbc.sql("insert into plaud_team_connections (" + COLUMNS + """
                ) values (:sourceId, :workspaceId, :clientId, :keyId, :teamName, :startFrom, :cutoff, :summaries,
                  :checkpoint, :status, :lastCheckedAt, :connectedAt)
                on conflict (source_id) do update set
                  team_name = excluded.team_name, feed_checkpoint = excluded.feed_checkpoint,
                  status = excluded.status, last_checked_at = excluded.last_checked_at
                where plaud_team_connections.workspace_id = excluded.workspace_id
                """)
                .param("sourceId", c.sourceId())
                .param("workspaceId", c.workspaceId())
                .param("clientId", c.clientId())
                .param("keyId", c.keyId())
                .param("teamName", c.teamName())
                .param("startFrom", c.startFrom().name())
                .param("cutoff", Timestamp.from(c.cutoff()))
                .param("summaries", c.includeSummaries())
                .param("checkpoint", c.checkpoint())
                .param("status", c.status().name())
                .param("lastCheckedAt", c.lastCheckedAt() == null ? null : Timestamp.from(c.lastCheckedAt()))
                .param("connectedAt", Timestamp.from(c.connectedAt()))
                .update();
    }

    @Override
    public Optional<PlaudTeamConnection> find(UUID workspaceId, UUID sourceId) {
        return jdbc.sql("select " + COLUMNS + " from plaud_team_connections"
                        + " where workspace_id = :workspaceId and source_id = :sourceId")
                .param("workspaceId", workspaceId)
                .param("sourceId", sourceId)
                .query(JdbcPlaudTeamConnections::toConnection)
                .optional();
    }

    @Override
    public List<PlaudTeamConnection> findAll(UUID workspaceId) {
        return jdbc.sql("select " + COLUMNS + " from plaud_team_connections where workspace_id = :workspaceId"
                        + " order by connected_at")
                .param("workspaceId", workspaceId)
                .query(JdbcPlaudTeamConnections::toConnection)
                .list();
    }

    private static PlaudTeamConnection toConnection(ResultSet row, int rowNumber) throws SQLException {
        return new PlaudTeamConnection(
                row.getObject("source_id", UUID.class),
                row.getObject("workspace_id", UUID.class),
                row.getString("client_id"),
                row.getString("key_id"),
                row.getString("team_name"),
                StartFrom.valueOf(row.getString("start_from")),
                row.getTimestamp("start_cutoff").toInstant(),
                row.getBoolean("include_summaries"),
                row.getString("feed_checkpoint"),
                ConnectionStatus.valueOf(row.getString("status")),
                instant(row.getTimestamp("last_checked_at")),
                row.getTimestamp("connected_at").toInstant());
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
