package com.conxist.capture.application;

import java.util.UUID;
import com.conxist.capture.DocumentOrigin;
import com.conxist.capture.domain.CapturedDocument;
import com.conxist.capture.domain.DocumentTitle;
import com.conxist.capture.domain.UploadPolicy;
import com.conxist.sources.Provider;
import com.conxist.sources.SourceCatalog;

/** Takes an uploaded file into an upload source: keep the file, read its text, send it to the hub. */
public final class CaptureUpload {

    private final CaptureSources sources;
    private final UploadPolicy policy;
    private final TextExtractor extractor;
    private final FileStore files;
    private final CapturedDocumentRepository documents;
    private final CaptureContext context;

    public CaptureUpload(SourceCatalog catalog, UploadPolicy policy, TextExtractor extractor, FileStore files,
            CapturedDocumentRepository documents, CaptureContext context) {
        this.sources = new CaptureSources(catalog);
        this.policy = policy;
        this.extractor = extractor;
        this.files = files;
        this.documents = documents;
        this.context = context;
    }

    public DocumentSummary capture(UUID sourceId, String fileName, byte[] content) {
        sources.require(sourceId, Provider.UPLOAD);
        String mediaType = extractor.detectMediaType(content, fileName);
        policy.check(mediaType, content.length);
        String text = extractor.extractText(content, mediaType);
        UUID workspaceId = context.workspace().id();
        String storageKey = files.store(workspaceId, sourceId, content);
        CapturedDocument document = new CapturedDocument(UUID.randomUUID(), workspaceId, sourceId,
                DocumentTitle.from(fileName), mediaType, content.length, text, DocumentOrigin.UPLOAD, storageKey,
                context.clock().instant());
        try {
            context.saveAndAnnounce(documents, document);
        } catch (RuntimeException failure) {
            // A file with no document pointing to it could never be found or deleted again.
            files.delete(storageKey);
            throw failure;
        }
        return DocumentSummary.of(document);
    }

}
