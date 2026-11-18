package com.conxist.capture.adapter.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import com.conxist.capture.DocumentOrigin;
import com.conxist.capture.application.CapturedDocumentRepository;
import com.conxist.capture.domain.CapturedDocument;
import com.conxist.capture.domain.DocumentTitle;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcCapturedDocumentRepository implements CapturedDocumentRepository {

    private final JdbcClient jdbc;

    JdbcCapturedDocumentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean saveIfNew(CapturedDocument document) {
        return jdbc.sql("""
                insert into captured_documents
                  (id, workspace_id, source_id, title, media_type, size_bytes, text_content, origin,
                   origin_reference, captured_at)
                values
                  (:id, :workspaceId, :sourceId, :title, :mediaType, :sizeBytes, :text, :origin,
                   :originReference, :capturedAt)
                on conflict (source_id, origin_reference) do nothing
                """)
                .param("id", document.id())
                .param("workspaceId", document.workspaceId())
                .param("sourceId", document.sourceId())
                .param("title", document.title().value())
                .param("mediaType", document.mediaType())
                .param("sizeBytes", document.sizeBytes())
                .param("text", document.text())
                .param("origin", document.origin().name())
                .param("originReference", document.originReference())
                .param("capturedAt", Timestamp.from(document.capturedAt()))
                .update() == 1;
    }

    @Override
    public List<CapturedDocument> findBySource(UUID workspaceId, UUID sourceId) {
        return jdbc.sql("""
                select id, workspace_id, source_id, title, media_type, size_bytes, text_content, origin,
                       origin_reference, captured_at
                from captured_documents
                where workspace_id = :workspaceId and source_id = :sourceId
                order by captured_at desc
                """)
                .param("workspaceId", workspaceId)
                .param("sourceId", sourceId)
                .query(JdbcCapturedDocumentRepository::toDocument)
                .list();
    }

    private static CapturedDocument toDocument(ResultSet row, int rowNumber) throws SQLException {
        return new CapturedDocument(
                row.getObject("id", UUID.class),
                row.getObject("workspace_id", UUID.class),
                row.getObject("source_id", UUID.class),
                new DocumentTitle(row.getString("title")),
                row.getString("media_type"),
                row.getLong("size_bytes"),
                row.getString("text_content"),
                DocumentOrigin.valueOf(row.getString("origin")),
                row.getString("origin_reference"),
                row.getTimestamp("captured_at").toInstant());
    }
}
