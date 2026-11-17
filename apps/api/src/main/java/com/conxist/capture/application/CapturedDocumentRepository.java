package com.conxist.capture.application;

import java.util.List;
import java.util.UUID;
import com.conxist.capture.domain.CapturedDocument;

public interface CapturedDocumentRepository {

    void save(CapturedDocument document);

    boolean contains(UUID sourceId, String originReference);

    List<CapturedDocument> findBySource(UUID workspaceId, UUID sourceId);
}
