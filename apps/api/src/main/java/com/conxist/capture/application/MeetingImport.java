package com.conxist.capture.application;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.conxist.capture.DocumentOrigin;
import com.conxist.capture.application.PlaudTeam.Transcript;
import com.conxist.capture.domain.CapturedDocument;
import com.conxist.capture.domain.DocumentTitle;
import com.conxist.capture.domain.MeetingText;

/** Saves one recorded meeting as a document waiting for review, once per recorder file id. */
final class MeetingImport {

    private final CapturedDocumentRepository documents;
    private final CaptureContext context;
    private final int maxTextCharacters;

    MeetingImport(CapturedDocumentRepository documents, CaptureContext context, int maxTextCharacters) {
        this.documents = documents;
        this.context = context;
        this.maxTextCharacters = maxTextCharacters;
    }

    boolean alreadyImported(UUID workspaceId, UUID sourceId, String fileId) {
        return documents.exists(workspaceId, sourceId, fileId);
    }

    /** False when another sync saved the same meeting first. */
    boolean save(UUID workspaceId, UUID sourceId, String fileId, String rawTitle, Instant recordedAt,
            Transcript transcript, List<String> summaries) {
        DocumentTitle title = DocumentTitle.ofMeeting(rawTitle);
        String text = MeetingText.compose(title.value(), recordedAt, transcript.language(), summaries,
                transcript.lines(), maxTextCharacters);
        CapturedDocument document = new CapturedDocument(UUID.randomUUID(), workspaceId, sourceId, title,
                MeetingText.MEDIA_TYPE, text.getBytes(StandardCharsets.UTF_8).length, text, DocumentOrigin.RECORDING,
                fileId, context.clock().instant());
        return context.saveAndAnnounce(documents, document);
    }
}
