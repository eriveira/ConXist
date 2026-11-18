package com.conxist.capture.application;

import java.util.List;
import java.util.UUID;
import com.conxist.capture.domain.CapturedDocument;

public interface CapturedDocumentRepository {

    /** Saves the document unless its source already holds one with the same origin reference. */
    boolean saveIfNew(CapturedDocument document);

    boolean exists(UUID workspaceId, UUID sourceId, String originReference);

    List<CapturedDocument> findBySource(UUID workspaceId, UUID sourceId);
}
