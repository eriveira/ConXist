package com.conxist.capture.adapter.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.conxist.capture.application.SyncHistory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcSyncHistory implements SyncHistory {

    private final JdbcClient jdbc;

    JdbcSyncHistory(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(SyncRun run) {
        jdbc.sql("""
                insert into source_syncs (id, workspace_id, source_id, started_at, finished_at, started_by, outcome,
                  imported, already_imported, waiting, queued, problem_code)
                values (:id, :workspaceId, :sourceId, :startedAt, :finishedAt, :startedBy, :outcome,
                  :imported, :already, :waiting, :queued, :problem)
                """)
                .param("id", run.id())
                .param("workspaceId", run.workspaceId())
                .param("sourceId", run.sourceId())
                .param("startedAt", Timestamp.from(run.startedAt()))
                .param("finishedAt", Timestamp.from(run.finishedAt()))
                .param("startedBy", run.startedBy().name())
                .param("outcome", run.outcome().name())
                .param("imported", run.imported())
                .param("already", run.alreadyImported())
                .param("waiting", run.waiting())
                .param("queued", run.queued())
                .param("problem", run.problemCode())
                .update();
    }

    @Override
    public List<SyncRun> recent(UUID workspaceId, UUID sourceId, boolean changesOnly, int limit) {
        return jdbc.sql("""
                select id, workspace_id, source_id, started_at, finished_at, started_by, outcome, imported,
                       already_imported, waiting, queued, problem_code
                from source_syncs
                where workspace_id = :workspaceId and source_id = :sourceId
                  and (not :changesOnly or outcome <> 'UP_TO_DATE')
                order by started_at desc
                limit :limit
                """)
                .param("workspaceId", workspaceId)
                .param("sourceId", sourceId)
                .param("changesOnly", changesOnly)
                .param("limit", limit)
                .query((row, n) -> new SyncRun(
                        row.getObject("id", UUID.class),
                        row.getObject("workspace_id", UUID.class),
                        row.getObject("source_id", UUID.class),
                        row.getTimestamp("started_at").toInstant(),
                        row.getTimestamp("finished_at").toInstant(),
                        StartedBy.valueOf(row.getString("started_by")),
                        Outcome.valueOf(row.getString("outcome")),
                        row.getInt("imported"),
                        row.getInt("already_imported"),
                        row.getInt("waiting"),
                        row.getInt("queued"),
                        row.getString("problem_code")))
                .list();
    }

    @Override
    public void forgetBefore(UUID workspaceId, Instant before) {
        jdbc.sql("delete from source_syncs where workspace_id = :workspaceId and started_at < :before")
                .param("workspaceId", workspaceId)
                .param("before", Timestamp.from(before))
                .update();
    }
}
