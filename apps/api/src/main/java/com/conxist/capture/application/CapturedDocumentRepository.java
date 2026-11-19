package com.conxist.capture.application;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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

    /** What this workspace already has of these recordings, in any source; a reference not listed is new to it. */
    Map<String, Known> known(UUID workspaceId, Collection<String> originReferences);

    /** HELD: a source holds it now. DELETED: a person deleted it and ConXist keeps it out of that source. */
    enum Known { HELD, DELETED }

    List<CapturedDocument> findBySource(UUID workspaceId, UUID sourceId);

    Optional<CapturedDocument> find(UUID workspaceId, UUID sourceId, UUID documentId);
}
