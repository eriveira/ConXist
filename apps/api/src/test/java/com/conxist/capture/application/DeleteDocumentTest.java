package com.conxist.capture.application;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import com.conxist.capture.DocumentDeleted;
import com.conxist.capture.application.SyncRecorder.SyncResult;
import com.conxist.capture.domain.PlaudTeamConnection;
import com.conxist.capture.domain.StartFrom;
import com.conxist.capture.domain.UploadPolicy;
import com.conxist.platform.NotFound;
import com.conxist.sources.Provider;
import com.conxist.sources.SourceSummary;
import com.conxist.testing.FixedWorkspace;
import com.conxist.testing.ImmediateUnitOfWork;
import com.conxist.testing.RecordedEvents;
import com.conxist.testing.TestClock;
import org.junit.jupiter.api.Test;

class DeleteDocumentTest {

    private final CaptureFakes.Sources sources = new CaptureFakes.Sources();
    private final CaptureFakes.Plaud plaud = new CaptureFakes.Plaud();
    private final CaptureFakes.Connections connections = new CaptureFakes.Connections();
    private final CaptureFakes.Files files = new CaptureFakes.Files();
    private final CaptureFakes.Documents documents = new CaptureFakes.Documents();
    private final RecordedEvents events = new RecordedEvents();
    private final CaptureContext context = new CaptureContext(FixedWorkspace.standard(), events,
            new ImmediateUnitOfWork(), TestClock.fixed());
    private final SyncRecorder syncRecorder = new SyncRecorder(sources, plaud, connections,
            new CaptureFakes.Account(), new CaptureFakes.AccountConnections(), new CaptureFakes.Picks(), documents,
            new CaptureFakes.History(), context, 2_000_000);
    private final CaptureUpload captureUpload = new CaptureUpload(sources,
            new UploadPolicy(1_000, Set.of("text/plain")), new CaptureFakes.Extractor(), files, documents, context);
    private final DeleteDocument deleteDocument = new DeleteDocument(sources, documents, files, context);

    @Test
    void a_deleted_meeting_leaves_conxist_tells_the_hub_and_is_not_imported_again() {
        SourceSummary source = recorder();
        plaud.record("f1", "Sprint planning", TestClock.NOW.minus(Duration.ofDays(1)));
        syncRecorder.sync(source.id());
        UUID documentId = documents.saved.getFirst().id();

        deleteDocument.delete(source.id(), documentId);

        assertThat(documents.saved).isEmpty();
        assertThat(events.ofType(DocumentDeleted.class)).singleElement()
                .satisfies(event -> assertThat(event.documentId()).isEqualTo(documentId));
        plaud.checkpointExpired = true;
        assertThat(syncRecorder.sync(source.id())).isEqualTo(new SyncResult(0, 1, 0));
        assertThat(documents.saved).isEmpty();
        assertThat(plaud.transcriptCalls).isEqualTo(1);
    }

    @Test
    void a_deleted_upload_also_loses_its_stored_file_and_the_same_file_can_be_added_again() {
        SourceSummary uploads = sources.add(Provider.UPLOAD, "");
        byte[] notes = "Kickoff notes".getBytes(UTF_8);
        DocumentSummary captured = captureUpload.capture(uploads.id(), "notes.txt", notes);

        deleteDocument.delete(uploads.id(), captured.id());

        assertThat(files.stored).isEmpty();
        assertThat(documents.saved).isEmpty();
        captureUpload.capture(uploads.id(), "notes.txt", notes);
        assertThat(documents.saved).hasSize(1);
    }

    @Test
    void a_stored_file_that_cannot_be_removed_does_not_undo_the_delete() {
        SourceSummary uploads = sources.add(Provider.UPLOAD, "");
        DocumentSummary captured = captureUpload.capture(uploads.id(), "notes.txt", "Notes".getBytes(UTF_8));
        files.deleteFails = true;

        deleteDocument.delete(uploads.id(), captured.id());

        assertThat(documents.saved).isEmpty();
        assertThat(events.ofType(DocumentDeleted.class)).hasSize(1);
    }

    @Test
    void a_document_already_gone_or_of_another_source_is_not_found_and_nothing_is_announced() {
        SourceSummary uploads = sources.add(Provider.UPLOAD, "");
        SourceSummary other = sources.add(Provider.UPLOAD, "");
        DocumentSummary captured = captureUpload.capture(uploads.id(), "notes.txt", "Notes".getBytes(UTF_8));

        assertThatThrownBy(() -> deleteDocument.delete(other.id(), captured.id()))
                .isInstanceOf(NotFound.class).extracting(e -> ((NotFound) e).code()).isEqualTo("document.not-found");
        assertThatThrownBy(() -> deleteDocument.delete(uploads.id(), UUID.randomUUID()))
                .isInstanceOf(NotFound.class);
        assertThatThrownBy(() -> deleteDocument.delete(UUID.randomUUID(), captured.id()))
                .isInstanceOf(NotFound.class).extracting(e -> ((NotFound) e).code()).isEqualTo("source.not-found");
        assertThat(documents.saved).hasSize(1);
        assertThat(events.ofType(DocumentDeleted.class)).isEmpty();
    }

    private SourceSummary recorder() {
        SourceSummary source = sources.add(Provider.PLAUD, "Acme Engineering");
        connections.save(PlaudTeamConnection.connect(source.id(), FixedWorkspace.WORKSPACE_ID,
                CaptureFakes.Plaud.CLIENT_ID, "cx-0000000000000001", "Acme Engineering", StartFrom.LAST_30_DAYS, false,
                TestClock.NOW));
        return source;
    }
}
