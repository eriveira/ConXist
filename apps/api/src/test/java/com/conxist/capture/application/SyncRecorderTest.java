package com.conxist.capture.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import com.conxist.capture.DocumentCaptured;
import com.conxist.capture.DocumentOrigin;
import com.conxist.capture.application.RecorderConnector.Recording;
import com.conxist.capture.application.SyncRecorder.SyncResult;
import com.conxist.platform.RuleViolation;
import com.conxist.sources.Provider;
import com.conxist.sources.SourceSummary;
import com.conxist.testing.FixedWorkspace;
import com.conxist.testing.ImmediateUnitOfWork;
import com.conxist.testing.RecordedEvents;
import com.conxist.testing.TestClock;
import org.junit.jupiter.api.Test;

class SyncRecorderTest {

    private static final Recording KICKOFF = new Recording("rec-1", "Kickoff", Instant.EPOCH, "We start with uploads.");
    private static final Recording REVIEW = new Recording("rec-2", "Review", Instant.EPOCH, "Show it on the canvas.");

    private final CaptureFakes.Sources sources = new CaptureFakes.Sources();
    private final CaptureFakes.Recorder recorder = new CaptureFakes.Recorder();
    private final CaptureFakes.Documents documents = new CaptureFakes.Documents();
    private final RecordedEvents events = new RecordedEvents();
    private final SyncRecorder syncRecorder = new SyncRecorder(sources, recorder, documents,
            new CaptureContext(FixedWorkspace.standard(), events, new ImmediateUnitOfWork(), TestClock.fixed()));

    @Test
    void imports_each_transcript_as_a_document_from_the_signed_in_account() {
        SourceSummary plaud = sources.add(Provider.PLAUD, "Team account");
        recorder.recordings.add(KICKOFF);

        SyncResult result = syncRecorder.sync(plaud.id());

        assertThat(result).isEqualTo(new SyncResult(1, 0));
        assertThat(recorder.lastAccount).isEqualTo("Team account");
        assertThat(documents.saved).singleElement().satisfies(document -> {
            assertThat(document.text()).isEqualTo("We start with uploads.");
            assertThat(document.origin()).isEqualTo(DocumentOrigin.RECORDING);
            assertThat(document.originReference()).isEqualTo("rec-1");
        });
        assertThat(events.ofType(DocumentCaptured.class)).hasSize(1);
    }

    @Test
    void skips_recordings_already_imported() {
        SourceSummary plaud = sources.add(Provider.PLAUD, "Team account");
        recorder.recordings.add(KICKOFF);
        syncRecorder.sync(plaud.id());
        recorder.recordings.add(REVIEW);

        SyncResult second = syncRecorder.sync(plaud.id());

        assertThat(second).isEqualTo(new SyncResult(1, 1));
        assertThat(documents.saved).hasSize(2);
    }

    @Test
    void rejects_a_source_that_is_not_a_recorder() {
        SourceSummary uploads = sources.add(Provider.UPLOAD, "");

        assertThatThrownBy(() -> syncRecorder.sync(uploads.id())).isInstanceOf(RuleViolation.class);
    }
}
