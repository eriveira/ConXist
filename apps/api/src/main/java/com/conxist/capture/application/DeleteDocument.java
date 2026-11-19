package com.conxist.capture.application;

import java.util.UUID;
import com.conxist.capture.DocumentOrigin;
import com.conxist.capture.domain.CapturedDocument;
import com.conxist.sources.SourceCatalog;

/**
 * A person deletes a captured document from ConXist. The source itself is never changed: a recording stays in the
 * person's Plaud account, and ConXist remembers its id so later checks do not import it again. An uploaded file's
 * stored copy is removed too.
 */
public final class DeleteDocument {

    private static final System.Logger log = System.getLogger(DeleteDocument.class.getName());

    private final CaptureSources sources;
    private final CapturedDocumentRepository documents;
    private final FileStore files;
    private final CaptureContext context;

    public DeleteDocument(SourceCatalog catalog, CapturedDocumentRepository documents, FileStore files,
            CaptureContext context) {
        this.sources = new CaptureSources(catalog);
        this.documents = documents;
        this.files = files;
        this.context = context;
    }

    public void delete(UUID sourceId, UUID documentId) {
        sources.find(sourceId);
        CapturedDocument document = documents.find(context.workspace().id(), sourceId, documentId)
                .orElseThrow(CaptureSources::documentNotFound);
        boolean recording = document.origin() == DocumentOrigin.RECORDING;
        if (!context.deleteAndAnnounce(documents, document, recording)) {
            throw CaptureSources.documentNotFound();
        }
        // By ids only, like a read: never the title or the text.
        log.log(System.Logger.Level.INFO, "Document {0} of source {1} in workspace {2} deleted", documentId, sourceId,
                document.workspaceId());
        if (!recording) {
            removeStoredFile(document);
        }
    }

    /** The document is already gone; a stored file left behind is logged, by ids, rather than failing the delete. */
    private void removeStoredFile(CapturedDocument document) {
        // An upload's key names its workspace and source; any other key is not this document's file to remove.
        if (!document.originReference().startsWith(document.workspaceId() + "/" + document.sourceId() + "/")) {
            log.log(System.Logger.Level.WARNING, "Deleted document {0} of source {1} had an unexpected file key",
                    document.id(), document.sourceId());
            return;
        }
        try {
            files.delete(document.originReference());
        } catch (RuntimeException failure) {
            log.log(System.Logger.Level.WARNING, "Could not remove the stored file of deleted document {0} of source {1}: {2}",
                    document.id(), document.sourceId(), failure.getClass().getSimpleName());
        }
    }
}
