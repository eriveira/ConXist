package com.conxist.capture.adapter.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import com.conxist.capture.application.RecordingPicks;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcRecordingPicks implements RecordingPicks {

    private final JdbcClient jdbc;

    JdbcRecordingPicks(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void add(UUID workspaceId, UUID sourceId, Collection<Pick> picks, Instant at) {
        for (Pick pick : picks) {
            jdbc.sql("""
                    insert into plaud_recording_picks (source_id, workspace_id, file_id, recorded_at, picked_at)
                    values (:sourceId, :workspaceId, :fileId, :recordedAt, :at)
                    on conflict (source_id, file_id) do nothing
                    """)
                    .param("sourceId", sourceId)
                    .param("workspaceId", workspaceId)
                    .param("fileId", pick.fileId())
                    .param("recordedAt", Timestamp.from(pick.recordedAt()))
                    .param("at", Timestamp.from(at))
                    .update();
        }
    }

    @Override
    public List<Pick> next(UUID sourceId, int limit) {
        return jdbc.sql("""
                select file_id, recorded_at from plaud_recording_picks where source_id = :sourceId
                order by tried_at nulls first, recorded_at desc limit :limit
                """)
                .param("sourceId", sourceId)
                .param("limit", limit)
                .query((row, n) -> new Pick(row.getString("file_id"), row.getTimestamp("recorded_at").toInstant()))
                .list();
    }

    @Override
    public void remove(UUID sourceId, String fileId) {
        jdbc.sql("delete from plaud_recording_picks where source_id = :sourceId and file_id = :fileId")
                .param("sourceId", sourceId)
                .param("fileId", fileId)
                .update();
    }

    @Override
    public void tried(UUID sourceId, String fileId, Instant at) {
        jdbc.sql("update plaud_recording_picks set tried_at = :at where source_id = :sourceId and file_id = :fileId")
                .param("sourceId", sourceId)
                .param("fileId", fileId)
                .param("at", Timestamp.from(at))
                .update();
    }

    @Override
    public int giveUp(UUID sourceId, Instant pickedBefore) {
        return jdbc.sql("""
                delete from plaud_recording_picks
                where source_id = :sourceId and tried_at is not null and picked_at < :before
                """)
                .param("sourceId", sourceId)
                .param("before", Timestamp.from(pickedBefore))
                .update();
    }

    @Override
    public int count(UUID sourceId) {
        return jdbc.sql("select count(*) from plaud_recording_picks where source_id = :sourceId")
                .param("sourceId", sourceId)
                .query(Integer.class)
                .single();
    }
}
