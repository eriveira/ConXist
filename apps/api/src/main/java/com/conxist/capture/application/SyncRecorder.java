package com.conxist.capture.application;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import com.conxist.capture.DocumentOrigin;
import com.conxist.capture.application.RecorderConnector.Recording;
import com.conxist.capture.domain.CapturedDocument;
import com.conxist.capture.domain.DocumentTitle;
import com.conxist.sources.Provider;
import com.conxist.sources.SourceCatalog;
import com.conxist.sources.SourceSummary;

/** Imports the transcripts of new recordings from a recorder source. Recordings already imported are skipped. */
public final class SyncRecorder {

    static final String TRANSCRIPT_MEDIA_TYPE = "text/plain";

    private final CaptureSources sources;
    private final RecorderConnector recorder;
    private final CapturedDocumentRepository documents;
    private final CaptureContext context;

    public SyncRecorder(SourceCatalog catalog, RecorderConnector recorder, CapturedDocumentRepository documents,
            CaptureContext context) {
        this.sources = new CaptureSources(catalog);
        this.recorder = recorder;
        this.documents = documents;
        this.context = context;
    }

    public SyncResult sync(UUID sourceId) {
        SourceSummary source = sources.require(sourceId, Provider.PLAUD);
        int imported = 0;
        int skipped = 0;
        for (Recording recording : recorder.recordings(source.accountLabel())) {
            if (context.saveAndAnnounce(documents, toDocument(sourceId, recording))) {
                imported++;
            } else {
                skipped++;
            }
        }
        return new SyncResult(imported, skipped);
    }

    private CapturedDocument toDocument(UUID sourceId, Recording recording) {
        String transcript = recording.transcript();
        return new CapturedDocument(UUID.randomUUID(), context.workspace().id(), sourceId,
                DocumentTitle.from(recording.title()), TRANSCRIPT_MEDIA_TYPE,
                transcript.getBytes(StandardCharsets.UTF_8).length, transcript, DocumentOrigin.RECORDING,
                recording.id(), context.clock().instant());
    }

    public record SyncResult(int imported, int alreadyImported) {
    }
}
