package com.conxist.capture.application;

import java.util.List;
import java.util.UUID;
import com.conxist.platform.CurrentWorkspace;

/** Lists what a source has captured, newest first. */
public final class ListDocuments {

    private final CapturedDocumentRepository documents;
    private final CurrentWorkspace workspace;

    public ListDocuments(CapturedDocumentRepository documents, CurrentWorkspace workspace) {
        this.documents = documents;
        this.workspace = workspace;
    }

    public List<DocumentSummary> list(UUID sourceId) {
        return documents.findBySource(workspace.id(), sourceId).stream().map(DocumentSummary::of).toList();
    }
}
