package com.conxist.capture.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.conxist.capture.domain.CapturedDocument;

public interface CapturedDocumentRepository {

    /** Saves the document unless its source already holds one with the same origin reference. */
    boolean saveIfNew(CapturedDocument document);

    /** True when the source holds this document, or a person deleted it and asked ConXist to keep it out. */
    boolean exists(UUID workspaceId, UUID sourceId, String originReference);

    /**
     * Removes the document. With {@code keepOut}, its origin reference is remembered so that it is never captured
     * into the same source again. Returns false when it was already gone.
     */
    boolean delete(CapturedDocument document, boolean keepOut, Instant deletedAt);

    List<CapturedDocument> findBySource(UUID workspaceId, UUID sourceId);

    Optional<CapturedDocument> find(UUID workspaceId, UUID sourceId, UUID documentId);
}
