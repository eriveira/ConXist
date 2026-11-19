package com.conxist.capture.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Duration;
import java.time.Instant;
import com.conxist.capture.DocumentCaptured;
import com.conxist.capture.DocumentOrigin;
import com.conxist.capture.application.PlaudTeam.TeamFile;
import com.conxist.capture.application.SyncRecorder.SyncResult;
import com.conxist.capture.domain.ConnectionStatus;
import com.conxist.capture.domain.MeetingText;
import com.conxist.capture.domain.PlaudTeamConnection;
import com.conxist.capture.domain.StartFrom;
import com.conxist.platform.RuleViolation;
import com.conxist.sources.Provider;
import com.conxist.sources.SourceSummary;
import com.conxist.testing.FixedWorkspace;
import com.conxist.testing.ImmediateUnitOfWork;
import com.conxist.testing.RecordedEvents;
import com.conxist.testing.TestClock;
import org.junit.jupiter.api.Test;

class SyncRecorderTest {

    private static final Instant RECENT = TestClock.NOW.minus(Duration.ofDays(2));
    private static final Instant LONG_AGO = TestClock.NOW.minus(Duration.ofDays(60));

    private final CaptureFakes.Sources sources = new CaptureFakes.Sources();
    private final CaptureFakes.Plaud plaud = new CaptureFakes.Plaud();
    private final CaptureFakes.Connections connections = new CaptureFakes.Connections();
    private final CaptureFakes.Documents documents = new CaptureFakes.Documents();
    private final RecordedEvents events = new RecordedEvents();
    private final CaptureFakes.Account account = new CaptureFakes.Account();
    private final CaptureFakes.AccountConnections accountConnections = new CaptureFakes.AccountConnections();
    private final CaptureFakes.History history = new CaptureFakes.History();
    private final SyncRecorder syncRecorder = new SyncRecorder(sources, plaud, connections, account,
            accountConnections, new CaptureFakes.Picks(), documents, history,
            new CaptureContext(FixedWorkspace.standard(), events, new ImmediateUnitOfWork(), TestClock.fixed()),
            2_000_000);

    @Test
    void imports_each_new_meeting_with_its_summary_as_a_document_waiting_for_review() {
        SourceSummary source = connected(StartFrom.LAST_30_DAYS, true);
        plaud.record("f1", "Sprint planning", RECENT);

        SyncResult result = syncRecorder.sync(source.id());

        assertThat(result).isEqualTo(new SyncResult(1, 0, 0));
        assertThat(documents.saved).singleElement().satisfies(document -> {
            assertThat(document.title().value()).isEqualTo("Sprint planning");
            assertThat(document.mediaType()).isEqualTo(MeetingText.MEDIA_TYPE);
            assertThat(document.text()).contains("Summary of Sprint planning", "Speaker 1: Text of Sprint planning");
            assertThat(document.origin()).isEqualTo(DocumentOrigin.RECORDING);
            assertThat(document.originReference()).isEqualTo("f1");
        });
        assertThat(events.ofType(DocumentCaptured.class)).hasSize(1);
    }

    @Test
    void reads_every_page_and_continues_from_the_saved_checkpoint_next_time() {
        SourceSummary source = connected(StartFrom.LAST_30_DAYS, false);
        plaud.record("f1", "One", RECENT);
        plaud.record("f2", "Two", RECENT);
        plaud.record("f3", "Three", RECENT);

        assertThat(syncRecorder.sync(source.id()).imported()).isEqualTo(3);
        plaud.record("f4", "Four", RECENT);
        SyncResult second = syncRecorder.sync(source.id());

        assertThat(second).isEqualTo(new SyncResult(1, 0, 0));
        assertThat(plaud.checkpointsAsked).endsWith("3");
        PlaudTeamConnection saved = connections.bySource.get(source.id());
        assertThat(saved.checkpoint()).isEqualTo("4");
        assertThat(saved.lastCheckedAt()).isEqualTo(TestClock.NOW);
    }

    @Test
    void leaves_out_meetings_before_the_chosen_start_deleted_ones_and_summaries_when_not_chosen() {
        SourceSummary source = connected(StartFrom.LAST_30_DAYS, false);
        plaud.record("old", "Last quarter", LONG_AGO);
        plaud.feed.add(new TeamFile("gone", "Deleted", true, RECENT, true, false));
        plaud.record("f1", "Recent", RECENT);

        assertThat(syncRecorder.sync(source.id()).imported()).isEqualTo(1);
        assertThat(documents.saved).singleElement().satisfies(d -> {
            assertThat(d.originReference()).isEqualTo("f1");
            assertThat(d.text()).doesNotContain("Summary of");
        });
    }

    @Test
    void counts_meetings_still_being_transcribed_and_imports_them_when_they_come_back_ready() {
        SourceSummary source = connected(StartFrom.LAST_7_DAYS, false);
        plaud.feed.add(new TeamFile("f1", "Call", false, RECENT, false, false));

        assertThat(syncRecorder.sync(source.id())).isEqualTo(new SyncResult(0, 0, 1));
        plaud.record("f1", "Call", RECENT);
        assertThat(syncRecorder.sync(source.id())).isEqualTo(new SyncResult(1, 0, 0));
    }

    @Test
    void does_not_fetch_a_transcript_it_already_has() {
        SourceSummary source = connected(StartFrom.LAST_30_DAYS, false);
        plaud.record("f1", "Call", RECENT);
        syncRecorder.sync(source.id());
        plaud.feed.add(plaud.feed.getFirst());

        SyncResult again = syncRecorder.sync(source.id());

        assertThat(again).isEqualTo(new SyncResult(0, 1, 0));
        assertThat(plaud.transcriptCalls).isEqualTo(1);
    }

    @Test
    void reads_the_feed_from_the_start_when_plaud_forgot_the_checkpoint() {
        SourceSummary source = connected(StartFrom.LAST_30_DAYS, false);
        plaud.record("f1", "One", RECENT);
        syncRecorder.sync(source.id());
        plaud.checkpointExpired = true;

        SyncResult result = syncRecorder.sync(source.id());

        assertThat(result).isEqualTo(new SyncResult(0, 1, 0));
        assertThat(plaud.checkpointsAsked).endsWith("1", (String) null);
    }

    @Test
    void pauses_the_connection_when_plaud_refuses_the_key_while_reading_again_from_the_start() {
        SourceSummary source = connected(StartFrom.LAST_30_DAYS, false);
        plaud.record("f1", "One", RECENT);
        syncRecorder.sync(source.id());
        plaud.checkpointExpired = true;
        plaud.denyAfterExpiry = true;

        assertThatThrownBy(() -> syncRecorder.sync(source.id()))
                .isInstanceOf(RuleViolation.class)
                .extracting(e -> ((RuleViolation) e).code()).isEqualTo("plaud.access-lost");
        assertThat(connections.bySource.get(source.id()).status()).isEqualTo(ConnectionStatus.PAUSED);
    }

    @Test
    void stops_and_keeps_the_checkpoint_when_the_feed_says_more_but_the_cursor_does_not_move() {
        SourceSummary source = connected(StartFrom.LAST_30_DAYS, false);
        plaud.stuckCursor = true;

        assertThatThrownBy(() -> syncRecorder.sync(source.id()))
                .isInstanceOf(RuleViolation.class)
                .extracting(e -> ((RuleViolation) e).code()).isEqualTo("plaud.unavailable");
        assertThat(connections.bySource.get(source.id()).lastCheckedAt()).isNull();
    }

    @Test
    void each_check_goes_into_the_import_history_with_who_started_it_and_what_came_of_it() {
        SourceSummary source = connected(StartFrom.LAST_30_DAYS, false);
        plaud.record("f1", "Sprint planning", RECENT);

        syncRecorder.sync(source.id());
        syncRecorder.syncAll();
        plaud.down = true;
        assertThatThrownBy(() -> syncRecorder.sync(source.id())).isInstanceOf(RuleViolation.class);

        assertThat(history.runs).extracting(SyncHistory.SyncRun::startedBy, SyncHistory.SyncRun::outcome,
                SyncHistory.SyncRun::imported, SyncHistory.SyncRun::problemCode).containsExactly(
                tuple(SyncHistory.StartedBy.PERSON, SyncHistory.Outcome.IMPORTED, 1, null),
                tuple(SyncHistory.StartedBy.SCHEDULE, SyncHistory.Outcome.UP_TO_DATE, 0, null),
                tuple(SyncHistory.StartedBy.PERSON, SyncHistory.Outcome.FAILED, 0, "plaud.unavailable"));
        assertThat(history.forgotBefore).isEqualTo(TestClock.NOW.minus(SyncHistory.KEEP));
    }

    @Test
    void a_history_that_cannot_be_written_changes_neither_the_result_nor_the_problem_reported() {
        SourceSummary source = connected(StartFrom.LAST_30_DAYS, false);
        plaud.record("f1", "Sprint planning", RECENT);
        history.broken = true;

        assertThat(syncRecorder.sync(source.id())).isEqualTo(new SyncResult(1, 0, 0));
        assertThat(documents.saved).hasSize(1);
        assertThat(syncRecorder.syncAll()).singleElement()
                .satisfies(sync -> assertThat(sync.problemCode()).isNull());
        plaud.down = true;
        assertThatThrownBy(() -> syncRecorder.sync(source.id()))
                .isInstanceOf(RuleViolation.class)
                .extracting(e -> ((RuleViolation) e).code()).isEqualTo("plaud.unavailable");
    }

    @Test
    void the_scheduled_check_keeps_going_after_an_unexpected_failure() {
        SourceSummary source = connected(StartFrom.LAST_30_DAYS, false);
        plaud.broken = true;

        assertThat(syncRecorder.syncAll(FixedWorkspace.WORKSPACE_ID)).singleElement().satisfies(sync -> {
            assertThat(sync.sourceId()).isEqualTo(source.id());
            assertThat(sync.problemCode()).isEqualTo(SyncRecorder.SYNC_FAILED);
        });
    }

    @Test
    void pauses_the_connection_and_says_so_when_plaud_stops_accepting_the_key() {
        SourceSummary source = connected(StartFrom.LAST_30_DAYS, false);
        plaud.denying = true;

        assertThatThrownBy(() -> syncRecorder.sync(source.id()))
                .isInstanceOf(RuleViolation.class)
                .extracting(e -> ((RuleViolation) e).code()).isEqualTo("plaud.access-lost");
        assertThat(connections.bySource.get(source.id()).status()).isEqualTo(ConnectionStatus.PAUSED);

        plaud.denying = false;
        syncRecorder.sync(source.id());
        assertThat(connections.bySource.get(source.id()).status()).isEqualTo(ConnectionStatus.ACTIVE);
    }

    @Test
    void the_scheduled_check_skips_paused_sources_and_keeps_going_after_a_failure() {
        SourceSummary first = connected(StartFrom.LAST_30_DAYS, false);
        SourceSummary paused = connected(StartFrom.LAST_30_DAYS, false);
        connections.save(connections.bySource.get(paused.id()).paused(TestClock.NOW));
        plaud.record("f1", "One", RECENT);

        assertThat(syncRecorder.syncAll(FixedWorkspace.WORKSPACE_ID)).singleElement().satisfies(sync -> {
            assertThat(sync.sourceId()).isEqualTo(first.id());
            assertThat(sync.result().imported()).isEqualTo(1);
        });

        plaud.down = true;
        assertThat(syncRecorder.syncAll(FixedWorkspace.WORKSPACE_ID)).singleElement()
                .satisfies(sync -> assertThat(sync.problemCode()).isEqualTo("plaud.unavailable"));
    }

    @Test
    void refuses_a_recorder_source_that_was_never_connected_and_a_source_of_another_kind() {
        SourceSummary unconnected = sources.add(Provider.PLAUD, "");
        SourceSummary uploads = sources.add(Provider.UPLOAD, "");

        assertThatThrownBy(() -> syncRecorder.sync(unconnected.id())).isInstanceOf(RuleViolation.class);
        assertThatThrownBy(() -> syncRecorder.sync(uploads.id())).isInstanceOf(RuleViolation.class);
    }

    private SourceSummary connected(StartFrom startFrom, boolean summaries) {
        SourceSummary source = sources.add(Provider.PLAUD, "Acme Engineering");
        connections.save(PlaudTeamConnection.connect(source.id(), FixedWorkspace.WORKSPACE_ID,
                CaptureFakes.Plaud.CLIENT_ID, "cx-0000000000000001", "Acme Engineering", startFrom, summaries,
                TestClock.NOW));
        return source;
    }
}
