package com.conxist.capture.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.conxist.capture.MeetingLibrary;
import com.conxist.capture.domain.CapturedDocument;
import com.conxist.platform.CurrentWorkspace;
import com.conxist.sources.SourceCatalog;

/** The captures of the current workspace, for another module to read; a source of another workspace reads as empty. */
public final class ReadMeetingLibrary implements MeetingLibrary {

    private final SourceCatalog sources;
    private final CapturedDocumentRepository documents;
    private final CurrentWorkspace workspace;

    public ReadMeetingLibrary(SourceCatalog sources, CapturedDocumentRepository documents, CurrentWorkspace workspace) {
        this.sources = sources;
        this.documents = documents;
        this.workspace = workspace;
    }

    @Override
    public List<Entry> list(UUID sourceId) {
        if (sources.find(sourceId).isEmpty()) {
            return List.of();
        }
        return documents.findBySource(workspace.id(), sourceId).stream().map(DocumentSummary::of)
                .map(summary -> new Entry(summary.id(), summary.sourceId(), summary.title(), summary.origin(),
                        summary.capturedAt(), summary.recordedAt(), summary.textLength()))
                .toList();
    }

    @Override
    public Optional<Text> read(UUID sourceId, UUID meetingId) {
        if (sources.find(sourceId).isEmpty()) {
            return Optional.empty();
        }
        return documents.find(workspace.id(), sourceId, meetingId).map(ReadMeetingLibrary::text);
    }

    private static Text text(CapturedDocument document) {
        return new Text(document.id(), document.sourceId(), document.title().value(), document.origin(),
                document.text());
    }
}
