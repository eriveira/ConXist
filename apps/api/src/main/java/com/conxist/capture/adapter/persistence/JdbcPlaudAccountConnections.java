package com.conxist.capture.adapter.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.conxist.capture.application.PlaudAccountConnections;
import com.conxist.capture.domain.ConnectionStatus;
import com.conxist.capture.domain.PlaudAccountConnection;
import com.conxist.capture.domain.StartFrom;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcPlaudAccountConnections implements PlaudAccountConnections {

    private static final String COLUMNS = """
            source_id, workspace_id, account_label, account_key, start_from, start_cutoff, include_summaries, import_new,
            read_through, status, last_checked_at, connected_at""";

    private final JdbcClient jdbc;

    JdbcPlaudAccountConnections(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean add(PlaudAccountConnection c) {
        return insert(c, "on conflict (source_id) do nothing") == 1;
    }

    @Override
    public void save(PlaudAccountConnection c) {
        insert(c, """
                on conflict (source_id) do update set
                  read_through = excluded.read_through, status = excluded.status,
                  last_checked_at = excluded.last_checked_at
                where plaud_account_connections.workspace_id = excluded.workspace_id
                """);
    }

    private int insert(PlaudAccountConnection c, String onConflict) {
        return jdbc.sql("insert into plaud_account_connections (" + COLUMNS + """
                ) values (:sourceId, :workspaceId, :label, :key, :startFrom, :cutoff, :summaries, :importNew, :readThrough,
                  :status, :lastCheckedAt, :connectedAt)
                """ + onConflict)
                .param("sourceId", c.sourceId())
                .param("workspaceId", c.workspaceId())
                .param("label", c.accountLabel())
                .param("key", c.accountKey())
                .param("startFrom", c.startFrom().name())
                .param("cutoff", Timestamp.from(c.cutoff()))
                .param("summaries", c.includeSummaries())
                .param("importNew", c.importNew())
                .param("readThrough", timestamp(c.readThrough()))
                .param("status", c.status().name())
                .param("lastCheckedAt", timestamp(c.lastCheckedAt()))
                .param("connectedAt", Timestamp.from(c.connectedAt()))
                .update();
    }

    @Override
    public Optional<PlaudAccountConnection> find(UUID workspaceId, UUID sourceId) {
        return jdbc.sql("select " + COLUMNS + " from plaud_account_connections"
                        + " where workspace_id = :workspaceId and source_id = :sourceId")
                .param("workspaceId", workspaceId)
                .param("sourceId", sourceId)
                .query(JdbcPlaudAccountConnections::toConnection)
                .optional();
    }

    @Override
    public List<PlaudAccountConnection> findAll(UUID workspaceId) {
        return jdbc.sql("select " + COLUMNS + " from plaud_account_connections where workspace_id = :workspaceId"
                        + " order by connected_at")
                .param("workspaceId", workspaceId)
                .query(JdbcPlaudAccountConnections::toConnection)
                .list();
    }

    private static PlaudAccountConnection toConnection(ResultSet row, int rowNumber) throws SQLException {
        return new PlaudAccountConnection(
                row.getObject("source_id", UUID.class),
                row.getObject("workspace_id", UUID.class),
                row.getString("account_label"),
                row.getString("account_key"),
                StartFrom.valueOf(row.getString("start_from")),
                row.getTimestamp("start_cutoff").toInstant(),
                row.getBoolean("include_summaries"),
                row.getBoolean("import_new"),
                instant(row.getTimestamp("read_through")),
                ConnectionStatus.valueOf(row.getString("status")),
                instant(row.getTimestamp("last_checked_at")),
                row.getTimestamp("connected_at").toInstant());
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
