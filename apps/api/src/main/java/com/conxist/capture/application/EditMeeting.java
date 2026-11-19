package com.conxist.capture.application;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import com.conxist.capture.DocumentOrigin;
import com.conxist.capture.DocumentRetitled;
import com.conxist.capture.application.ReadDocument.DocumentView;
import com.conxist.capture.domain.CapturedDocument;
import com.conxist.capture.domain.DocumentTitle;
import com.conxist.capture.domain.MeetingText;
import com.conxist.capture.domain.RejectedDocument;
import com.conxist.platform.RuleViolation;
import com.conxist.sources.SourceCatalog;

/**
 * A person corrects a recorded meeting in ConXist: its title, speaker names and transcript lines, or puts back the
 * meeting as it was imported. Plaud is never changed. Each change names the edit it started from, so an older tab
 * cannot overwrite a newer edit.
 */
public final class EditMeeting {

    private static final System.Logger log = System.getLogger(EditMeeting.class.getName());

    private final CaptureSources sources;
    private final CapturedDocumentRepository documents;
    private final CaptureContext context;
    private final int maxTextCharacters;

    public EditMeeting(SourceCatalog catalog, CapturedDocumentRepository documents, CaptureContext context,
            int maxTextCharacters) {
        this.sources = new CaptureSources(catalog);
        this.documents = documents;
        this.context = context;
        this.maxTextCharacters = maxTextCharacters;
    }

    /**
     * {@code speakers} maps a speaker's name as it is now to the new one; {@code lines} maps a line's position in the
     * transcript to its new text. {@code editedAt} is when the meeting was last edited as the person saw it, or null.
     */
    public DocumentView edit(UUID sourceId, UUID documentId, Instant editedAt, String title, Map<String, String> speakers,
            Map<Integer, String> lines) {
        if (title == null || title.isBlank()) {
            throw new RejectedDocument("meeting.title-empty", "Give the meeting a title.");
        }
        CapturedDocument before = meeting(sourceId, documentId, editedAt);
        DocumentTitle newTitle = DocumentTitle.ofMeeting(title);
        String text = MeetingText.edit(before.text(), newTitle.value(), speakers, lines, maxTextCharacters);
        if (text.equals(before.text()) && newTitle.equals(before.title())) {
            return ReadDocument.view(before);
        }
        Instant at = context.clock().instant();
        boolean saved = context.unitOfWork().inTransaction(() -> {
            boolean changed = documents.saveEdit(before, newTitle, text, at);
            if (changed && !newTitle.equals(before.title())) {
                context.events().publish(new DocumentRetitled(before.id(), before.workspaceId(), before.sourceId(),
                        newTitle.value(), at));
            }
            return changed;
        });
        if (!saved) {
            throw changedMeanwhile();
        }
        // By ids only: never the title or the text.
        log.log(System.Logger.Level.INFO, "Document {0} of source {1} in workspace {2} edited", documentId, sourceId,
                before.workspaceId());
        return current(sourceId, documentId);
    }

    /** Puts back the meeting as it was imported. {@code editedAt} is the edit the person saw. */
    public DocumentView restore(UUID sourceId, UUID documentId, Instant editedAt) {
        CapturedDocument before = meeting(sourceId, documentId, editedAt);
        if (before.editedAt() == null) {
            return ReadDocument.view(before);
        }
        Instant at = context.clock().instant();
        CapturedDocument restored = context.unitOfWork().inTransaction(() -> {
            CapturedDocument after = documents.restore(before).orElse(null);
            if (after != null && !after.title().equals(before.title())) {
                context.events().publish(new DocumentRetitled(after.id(), after.workspaceId(), after.sourceId(),
                        after.title().value(), at));
            }
            return after;
        });
        if (restored == null) {
            throw changedMeanwhile();
        }
        log.log(System.Logger.Level.INFO, "Document {0} of source {1} in workspace {2} restored", documentId, sourceId,
                before.workspaceId());
        return ReadDocument.view(restored);
    }

    private CapturedDocument meeting(UUID sourceId, UUID documentId, Instant editedAt) {
        sources.find(sourceId);
        CapturedDocument document = documents.find(context.workspace().id(), sourceId, documentId)
                .orElseThrow(CaptureSources::documentNotFound);
        if (document.origin() != DocumentOrigin.RECORDING) {
            throw new RejectedDocument("not-a-meeting", "Only recorded meetings can be edited.");
        }
        if (!Objects.equals(document.editedAt(), editedAt)) {
            throw changedMeanwhile();
        }
        return document;
    }

    private DocumentView current(UUID sourceId, UUID documentId) {
        return ReadDocument.view(documents.find(context.workspace().id(), sourceId, documentId)
                .orElseThrow(CaptureSources::documentNotFound));
    }

    private static RuleViolation changedMeanwhile() {
        return new RejectedDocument("changed", "Someone changed this meeting meanwhile. Open it again to see the latest.");
    }
}
