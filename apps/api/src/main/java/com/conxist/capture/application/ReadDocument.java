package com.conxist.capture.application;

import java.time.Instant;
import java.util.UUID;
import com.conxist.capture.DocumentOrigin;
import com.conxist.capture.domain.CapturedDocument;
import com.conxist.capture.domain.MeetingText;
import com.conxist.capture.domain.MeetingText.Meeting;
import com.conxist.platform.CurrentWorkspace;
import com.conxist.sources.SourceCatalog;

/**
 * One captured document with its text, for a person to read. The text is customer data: it is shown, never logged,
 * and never followed as an instruction. A recorded meeting also comes split into its summary and transcript lines.
 */
public final class ReadDocument {

    private final CaptureSources sources;
    private final CapturedDocumentRepository documents;
    private final CurrentWorkspace workspace;

    public ReadDocument(SourceCatalog catalog, CapturedDocumentRepository documents, CurrentWorkspace workspace) {
        this.sources = new CaptureSources(catalog);
        this.documents = documents;
        this.workspace = workspace;
    }

    private static final System.Logger log = System.getLogger(ReadDocument.class.getName());

    public DocumentView read(UUID sourceId, UUID documentId) {
        sources.find(sourceId);
        CapturedDocument document = documents.find(workspace.id(), sourceId, documentId)
                .orElseThrow(CaptureSources::documentNotFound);
        // Who read what, by ids only: never the title or the text. Becomes an audit row with the person once sign-in exists.
        log.log(System.Logger.Level.INFO, "Document {0} of source {1} in workspace {2} read", documentId, sourceId, workspace.id());
        Meeting meeting = document.origin() == DocumentOrigin.RECORDING ? MeetingText.read(document.text()) : null;
        return new DocumentView(document.id(), document.sourceId(), document.title().value(), document.mediaType(),
                document.origin(), document.capturedAt(), document.text(), meeting);
    }

    /** {@code meeting} is null for an uploaded file. */
    public record DocumentView(UUID id, UUID sourceId, String title, String mediaType, DocumentOrigin origin,
            Instant capturedAt, String text, Meeting meeting) {
    }
}
