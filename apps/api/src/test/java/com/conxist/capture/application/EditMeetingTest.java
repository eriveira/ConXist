package com.conxist.capture.application;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.conxist.capture.DocumentRetitled;
import com.conxist.capture.application.ReadDocument.DocumentView;
import com.conxist.capture.domain.PlaudTeamConnection;
import com.conxist.capture.domain.StartFrom;
import com.conxist.capture.domain.UploadPolicy;
import com.conxist.platform.RuleViolation;
import com.conxist.sources.Provider;
import com.conxist.sources.SourceSummary;
import com.conxist.testing.FixedWorkspace;
import com.conxist.testing.ImmediateUnitOfWork;
import com.conxist.testing.RecordedEvents;
import com.conxist.testing.TestClock;
import org.junit.jupiter.api.Test;

class EditMeetingTest {

    private final CaptureFakes.Sources sources = new CaptureFakes.Sources();
    private final CaptureFakes.Plaud plaud = new CaptureFakes.Plaud();
    private final CaptureFakes.Connections connections = new CaptureFakes.Connections();
    private final CaptureFakes.Documents documents = new CaptureFakes.Documents();
    private final RecordedEvents events = new RecordedEvents();
    private final CaptureContext context = new CaptureContext(FixedWorkspace.standard(), events,
            new ImmediateUnitOfWork(), TestClock.fixed());
    private final SyncRecorder syncRecorder = new SyncRecorder(sources, plaud, connections,
            new CaptureFakes.Account(), new CaptureFakes.AccountConnections(), new CaptureFakes.Picks(), documents,
            new CaptureFakes.History(), context, 2_000_000);
    private final EditMeeting editMeeting = new EditMeeting(sources, documents, context, 2_000_000);

    @Test
    void an_edit_changes_title_speakers_and_lines_in_conxist_and_tells_the_hub_the_new_title() {
        SourceSummary source = recorder();
        UUID id = imported(source);
        String speaker = editMeeting.edit(source.id(), id, null, "Sprint planning", Map.of(), Map.of())
                .meeting().lines().getFirst().speaker();

        DocumentView edited = editMeeting.edit(source.id(), id, null, "Sprint 42 planning", Map.of(speaker, "Ana"),
                Map.of(0, "Fixed line."));

        assertThat(edited.title()).isEqualTo("Sprint 42 planning");
        assertThat(edited.editedAt()).isEqualTo(TestClock.NOW);
        assertThat(edited.meeting().lines().getFirst().speaker()).isEqualTo("Ana");
        assertThat(edited.meeting().lines().getFirst().text()).isEqualTo("Fixed line.");
        assertThat(events.ofType(DocumentRetitled.class)).singleElement()
                .satisfies(event -> assertThat(event.title()).isEqualTo("Sprint 42 planning"));
        assertThat(DocumentSummary.of(documents.saved.getFirst()).editedAt()).isEqualTo(TestClock.NOW);
    }

    @Test
    void an_edit_that_changes_nothing_saves_nothing() {
        SourceSummary source = recorder();
        UUID id = imported(source);

        DocumentView view = editMeeting.edit(source.id(), id, null, "Sprint planning", Map.of(), Map.of());

        assertThat(view.editedAt()).isNull();
        assertThat(events.ofType(DocumentRetitled.class)).isEmpty();
    }

    @Test
    void an_edit_from_an_older_read_is_refused_instead_of_overwriting_a_newer_one() {
        SourceSummary source = recorder();
        UUID id = imported(source);
        editMeeting.edit(source.id(), id, null, "First fix", Map.of(), Map.of());

        assertThatThrownBy(() -> editMeeting.edit(source.id(), id, null, "Second tab", Map.of(), Map.of()))
                .isInstanceOf(RuleViolation.class)
                .extracting(e -> ((RuleViolation) e).code()).isEqualTo("document.changed");
        assertThat(documents.saved.getFirst().title().value()).isEqualTo("First fix");
    }

    @Test
    void restore_puts_back_the_meeting_as_imported() {
        SourceSummary source = recorder();
        UUID id = imported(source);
        String before = documents.saved.getFirst().text();
        DocumentView edited = editMeeting.edit(source.id(), id, null, "Renamed", Map.of(), Map.of(0, "Changed."));

        DocumentView restored = editMeeting.restore(source.id(), id, edited.editedAt());

        assertThat(restored.title()).isEqualTo("Sprint planning");
        assertThat(restored.editedAt()).isNull();
        assertThat(restored.text()).isEqualTo(before);
        assertThat(events.ofType(DocumentRetitled.class)).extracting(DocumentRetitled::title)
                .containsExactly("Renamed", "Sprint planning");
    }

    @Test
    void refuses_an_empty_title_and_an_uploaded_file() {
        SourceSummary source = recorder();
        UUID id = imported(source);
        assertThatThrownBy(() -> editMeeting.edit(source.id(), id, null, " ", Map.of(), Map.of()))
                .extracting(e -> ((RuleViolation) e).code()).isEqualTo("document.meeting.title-empty");

        SourceSummary uploads = sources.add(Provider.UPLOAD, "");
        CaptureUpload upload = new CaptureUpload(sources, new UploadPolicy(1_000, Set.of("text/plain")),
                new CaptureFakes.Extractor(), new CaptureFakes.Files(), documents, context);
        UUID file = upload.capture(uploads.id(), "notes.txt", "Notes".getBytes(UTF_8)).id();
        assertThatThrownBy(() -> editMeeting.edit(uploads.id(), file, null, "Notes", Map.of(), Map.of()))
                .extracting(e -> ((RuleViolation) e).code()).isEqualTo("document.not-a-meeting");
    }

    private SourceSummary recorder() {
        SourceSummary source = sources.add(Provider.PLAUD, "Acme Engineering");
        connections.save(PlaudTeamConnection.connect(source.id(), FixedWorkspace.WORKSPACE_ID,
                CaptureFakes.Plaud.CLIENT_ID, "cx-0000000000000001", "Acme Engineering", StartFrom.LAST_30_DAYS, false,
                TestClock.NOW));
        return source;
    }

    private UUID imported(SourceSummary source) {
        plaud.record("f1", "Sprint planning", TestClock.NOW.minus(Duration.ofDays(1)));
        syncRecorder.sync(source.id());
        return documents.saved.getFirst().id();
    }
}
