package com.conxist.capture.application;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import java.util.UUID;
import com.conxist.capture.DocumentCaptured;
import com.conxist.capture.DocumentOrigin;
import com.conxist.capture.domain.RejectedDocument;
import com.conxist.capture.domain.UploadPolicy;
import com.conxist.platform.NotFound;
import com.conxist.platform.RuleViolation;
import com.conxist.sources.Provider;
import com.conxist.sources.SourceSummary;
import com.conxist.testing.FixedWorkspace;
import com.conxist.testing.ImmediateUnitOfWork;
import com.conxist.testing.RecordedEvents;
import com.conxist.testing.TestClock;
import org.junit.jupiter.api.Test;

class CaptureUploadTest {

    private static final byte[] NOTES = "Requirement: every source connects to the hub.".getBytes(UTF_8);

    private final CaptureFakes.Sources sources = new CaptureFakes.Sources();
    private final CaptureFakes.Extractor extractor = new CaptureFakes.Extractor();
    private final CaptureFakes.Files files = new CaptureFakes.Files();
    private final CaptureFakes.Documents documents = new CaptureFakes.Documents();
    private final RecordedEvents events = new RecordedEvents();
    private final CaptureContext context = new CaptureContext(FixedWorkspace.standard(), events,
            new ImmediateUnitOfWork(), TestClock.fixed());
    private final CaptureUpload captureUpload = new CaptureUpload(sources,
            new UploadPolicy(1_000, Set.of("text/plain")), extractor, files, documents, context);

    @Test
    void stores_the_file_keeps_its_text_and_announces_it() {
        SourceSummary uploads = sources.add(Provider.UPLOAD, "");

        DocumentSummary captured = captureUpload.capture(uploads.id(), "notes.txt", NOTES);

        assertThat(files.stored).containsExactly(NOTES);
        assertThat(documents.saved).singleElement().satisfies(document -> {
            assertThat(document.text()).isEqualTo(new String(NOTES, UTF_8));
            assertThat(document.originReference()).isEqualTo("key-1");
            assertThat(document.workspaceId()).isEqualTo(FixedWorkspace.WORKSPACE_ID);
        });
        assertThat(captured.title()).isEqualTo("notes.txt");
        assertThat(captured.origin()).isEqualTo(DocumentOrigin.UPLOAD);
        assertThat(captured.capturedAt()).isEqualTo(TestClock.NOW);
        assertThat(events.ofType(DocumentCaptured.class)).singleElement()
                .satisfies(event -> assertThat(event.documentId()).isEqualTo(captured.id()));
    }

    @Test
    void removes_the_stored_file_when_the_document_cannot_be_saved() {
        SourceSummary uploads = sources.add(Provider.UPLOAD, "");
        documents.failing = true;

        assertThatThrownBy(() -> captureUpload.capture(uploads.id(), "notes.txt", NOTES))
                .isInstanceOf(IllegalStateException.class);
        assertThat(files.stored).isEmpty();
        assertThat(events.isEmpty()).isTrue();
    }

    @Test
    void rejects_an_unknown_source() {
        assertThatThrownBy(() -> captureUpload.capture(UUID.randomUUID(), "notes.txt", NOTES))
                .isInstanceOf(NotFound.class);
        assertThat(files.stored).isEmpty();
    }

    @Test
    void rejects_a_source_that_does_not_take_uploads() {
        SourceSummary recorder = sources.add(Provider.PLAUD, "Team account");

        assertThatThrownBy(() -> captureUpload.capture(recorder.id(), "notes.txt", NOTES))
                .isInstanceOf(RuleViolation.class);
    }

    @Test
    void stores_nothing_when_the_type_is_not_accepted() {
        SourceSummary uploads = sources.add(Provider.UPLOAD, "");
        extractor.mediaType = "application/zip";

        assertThatThrownBy(() -> captureUpload.capture(uploads.id(), "archive.zip", NOTES))
                .isInstanceOf(RejectedDocument.class);
        assertThat(files.stored).isEmpty();
        assertThat(events.isEmpty()).isTrue();
    }

    @Test
    void stores_nothing_when_the_text_cannot_be_read() {
        SourceSummary uploads = sources.add(Provider.UPLOAD, "");
        extractor.unreadable = true;

        assertThatThrownBy(() -> captureUpload.capture(uploads.id(), "notes.txt", NOTES))
                .isInstanceOf(RejectedDocument.class);
        assertThat(files.stored).isEmpty();
        assertThat(documents.saved).isEmpty();
    }
}
