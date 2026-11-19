package com.conxist.capture.adapter.persistence;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import com.conxist.capture.DocumentOrigin;
import com.conxist.capture.adapter.secrets.SecretBox;
import com.conxist.capture.application.CapturedDocumentRepository;
import com.conxist.capture.domain.CapturedDocument;
import com.conxist.capture.domain.DocumentTitle;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Captured text is customer data, so it is stored encrypted ({@code text_sealed}), bound to its document id.
 * {@code text_content} only holds rows written before encryption, until {@link SealPlaintextDocuments} seals them.
 */
@Repository
class JdbcCapturedDocumentRepository implements CapturedDocumentRepository {

    private final JdbcClient jdbc;
    private final SecretBox box;

    JdbcCapturedDocumentRepository(JdbcClient jdbc, SecretBox box) {
        this.jdbc = jdbc;
        this.box = box;
    }

    @Override
    public boolean saveIfNew(CapturedDocument document) {
        lockReference(document);
        return jdbc.sql("""
                insert into captured_documents
                  (id, workspace_id, source_id, title, media_type, size_bytes, text_sealed, origin,
                   origin_reference, captured_at)
                select :id, :workspaceId, :sourceId, :title, :mediaType, :sizeBytes, :sealed, :origin,
                       :originReference, :capturedAt
                where not exists (select 1 from deleted_recordings
                                  where source_id = :sourceId and origin_reference = :originReference)
                on conflict (source_id, origin_reference) do nothing
                """)
                .param("id", document.id())
                .param("workspaceId", document.workspaceId())
                .param("sourceId", document.sourceId())
                .param("title", document.title().value())
                .param("mediaType", document.mediaType())
                .param("sizeBytes", document.sizeBytes())
                .param("sealed", seal(box, document.id(), document.text()))
                .param("origin", document.origin().name())
                .param("originReference", document.originReference())
                .param("capturedAt", Timestamp.from(document.capturedAt()))
                .update() == 1;
    }

    @Override
    public boolean exists(UUID workspaceId, UUID sourceId, String originReference) {
        return jdbc.sql("""
                select exists (select 1 from captured_documents
                  where workspace_id = :workspaceId and source_id = :sourceId and origin_reference = :reference)
                    or exists (select 1 from deleted_recordings
                  where workspace_id = :workspaceId and source_id = :sourceId and origin_reference = :reference)
                """)
                .param("workspaceId", workspaceId)
                .param("sourceId", sourceId)
                .param("reference", originReference)
                .query(Boolean.class)
                .single();
    }

    @Override
    public boolean delete(CapturedDocument document, boolean keepOut, Instant deletedAt) {
        if (keepOut) {
            lockReference(document);
            jdbc.sql("""
                    insert into deleted_recordings (workspace_id, source_id, origin_reference, deleted_at)
                    values (:workspaceId, :sourceId, :reference, :deletedAt)
                    on conflict (source_id, origin_reference) do nothing
                    """)
                    .param("workspaceId", document.workspaceId())
                    .param("sourceId", document.sourceId())
                    .param("reference", document.originReference())
                    .param("deletedAt", Timestamp.from(deletedAt))
                    .update();
        }
        return jdbc.sql("""
                delete from captured_documents
                where workspace_id = :workspaceId and source_id = :sourceId and id = :id
                """)
                .param("workspaceId", document.workspaceId())
                .param("sourceId", document.sourceId())
                .param("id", document.id())
                .update() == 1;
    }

    @Override
    public Map<String, Known> known(UUID workspaceId, Collection<String> originReferences) {
        if (originReferences.isEmpty()) {
            return Map.of();
        }
        Map<String, Known> known = new HashMap<>();
        jdbc.sql("""
                select origin_reference, 'DELETED' as known from deleted_recordings
                where workspace_id = :workspaceId and origin_reference in (:references)
                union all
                select origin_reference, 'HELD' from captured_documents
                where workspace_id = :workspaceId and origin = 'RECORDING' and origin_reference in (:references)
                """)
                .param("workspaceId", workspaceId)
                .param("references", List.copyOf(originReferences))
                .query((row, n) -> Map.entry(row.getString(1), Known.valueOf(row.getString(2))))
                .list()
                // Held wins: a recording deleted from one source and held by another is still in ConXist.
                .forEach(entry -> known.merge(entry.getKey(), entry.getValue(),
                        (a, b) -> a == Known.HELD || b == Known.HELD ? Known.HELD : Known.DELETED));
        return known;
    }

    @Override
    public List<CapturedDocument> findBySource(UUID workspaceId, UUID sourceId) {
        return jdbc.sql("""
                select id, workspace_id, source_id, title, media_type, size_bytes, text_content, text_sealed,
                       origin, origin_reference, captured_at
                from captured_documents
                where workspace_id = :workspaceId and source_id = :sourceId
                order by captured_at desc
                """)
                .param("workspaceId", workspaceId)
                .param("sourceId", sourceId)
                .query(this::toDocument)
                .list();
    }

    @Override
    public Optional<CapturedDocument> find(UUID workspaceId, UUID sourceId, UUID documentId) {
        return jdbc.sql("""
                select id, workspace_id, source_id, title, media_type, size_bytes, text_content, text_sealed,
                       origin, origin_reference, captured_at
                from captured_documents
                where workspace_id = :workspaceId and source_id = :sourceId and id = :id
                """)
                .param("workspaceId", workspaceId)
                .param("sourceId", sourceId)
                .param("id", documentId)
                .query(this::toDocument)
                .optional();
    }

    /**
     * A save and a delete of the same recording take turns, until the end of their transactions. Without it, a check
     * that is importing a recording while a person deletes it would wait on the unique index, find the old row gone
     * once the delete commits, and save the meeting again. Taking turns, the later one sees the other's work.
     */
    private void lockReference(CapturedDocument document) {
        jdbc.sql("select pg_advisory_xact_lock(hashtextextended(:key, 0))")
                .param("key", document.sourceId() + "/" + document.originReference())
                .query((row, n) -> 1)
                .single();
    }

    static byte[] seal(SecretBox box, UUID documentId, String text) {
        return box.seal(text.getBytes(StandardCharsets.UTF_8), documentId.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String text(ResultSet row, UUID documentId) throws SQLException {
        byte[] sealed = row.getBytes("text_sealed");
        if (sealed == null) {
            return row.getString("text_content");
        }
        return new String(box.open(sealed, documentId.toString().getBytes(StandardCharsets.UTF_8)),
                StandardCharsets.UTF_8);
    }

    private CapturedDocument toDocument(ResultSet row, int rowNumber) throws SQLException {
        UUID id = row.getObject("id", UUID.class);
        return new CapturedDocument(
                id,
                row.getObject("workspace_id", UUID.class),
                row.getObject("source_id", UUID.class),
                new DocumentTitle(row.getString("title")),
                row.getString("media_type"),
                row.getLong("size_bytes"),
                text(row, id),
                DocumentOrigin.valueOf(row.getString("origin")),
                row.getString("origin_reference"),
                row.getTimestamp("captured_at").toInstant());
    }
}
