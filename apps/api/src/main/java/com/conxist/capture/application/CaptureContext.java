package com.conxist.capture.application;

import java.time.Clock;
import com.conxist.capture.DocumentCaptured;
import com.conxist.capture.domain.CapturedDocument;
import com.conxist.platform.CurrentWorkspace;
import com.conxist.platform.EventPublisher;
import com.conxist.platform.UnitOfWork;

/** What every capture use case needs from the platform. */
public record CaptureContext(CurrentWorkspace workspace, EventPublisher events, UnitOfWork unitOfWork, Clock clock) {

    /**
     * Saves the document and tells the hub about it in the same transaction. Returns false, and announces nothing,
     * when the source already had this document.
     */
    boolean saveAndAnnounce(CapturedDocumentRepository documents, CapturedDocument document) {
        return unitOfWork.inTransaction(() -> {
            boolean saved = documents.saveIfNew(document);
            if (saved) {
                events.publish(new DocumentCaptured(document.id(), document.workspaceId(), document.sourceId(),
                        document.title().value(), document.origin(), document.capturedAt()));
            }
            return saved;
        });
    }
}
