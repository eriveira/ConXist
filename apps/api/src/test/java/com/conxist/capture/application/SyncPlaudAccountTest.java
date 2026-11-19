package com.conxist.capture.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import com.conxist.capture.application.SyncRecorder.SyncResult;
import com.conxist.capture.domain.ConnectionStatus;
import com.conxist.capture.domain.PlaudAccountConnection;
import com.conxist.capture.domain.StartFrom;
import com.conxist.platform.RuleViolation;
import com.conxist.sources.Provider;
import com.conxist.sources.SourceSummary;
import com.conxist.testing.FixedWorkspace;
import com.conxist.testing.ImmediateUnitOfWork;
import com.conxist.testing.RecordedEvents;
import com.conxist.testing.TestClock;
import org.junit.jupiter.api.Test;

/** Syncing a person's own Plaud account, through {@link SyncRecorder}. */
class SyncPlaudAccountTest {

    private static final Instant RECENT = TestClock.NOW.minus(Duration.ofDays(2));

    private final CaptureFakes.Sources sources = new CaptureFakes.Sources();
    private final CaptureFakes.Account account = new CaptureFakes.Account();
    private final CaptureFakes.AccountConnections connections = new CaptureFakes.AccountConnections();
    private final CaptureFakes.Documents documents = new CaptureFakes.Documents();
    private final CaptureFakes.Picks picks = new CaptureFakes.Picks();
    private final SyncRecorder sync = new SyncRecorder(sources, new CaptureFakes.Plaud(), new CaptureFakes.Connections(),
            account, connections, picks, documents, new CaptureFakes.History(),
            new CaptureContext(FixedWorkspace.standard(), new RecordedEvents(), new ImmediateUnitOfWork(),
                    TestClock.fixed()),
            2_000_000);

    @Test
    void imports_each_transcribed_meeting_in_range_with_its_notes_and_counts_the_ones_still_transcribing() {
        SourceSummary source = connected(StartFrom.LAST_30_DAYS, true);
        account.record("of_1", "Weekly sync", RECENT, true);
        account.record("of_2", "Customer call", TestClock.NOW.minus(Duration.ofDays(4)), true);
        account.record("of_3", "Stand-up", TestClock.NOW.minus(Duration.ofHours(1)), false);
        account.record("of_4", "Old retro", TestClock.NOW.minus(Duration.ofDays(50)), true);

        SyncResult result = sync.sync(source.id());

        assertThat(result).isEqualTo(new SyncResult(2, 0, 1));
        assertThat(documents.saved).extracting(d -> d.originReference()).containsExactlyInAnyOrder("of_1", "of_2");
        assertThat(documents.saved).allSatisfy(d -> assertThat(d.text()).contains("> Notes of", "Alex: Text of"));
        assertThat(connections.bySource.get(source.id()).lastCheckedAt()).isEqualTo(TestClock.NOW);
    }

    @Test
    void the_next_sync_looks_back_a_few_days_and_imports_a_meeting_once_its_transcript_is_ready() {
        SourceSummary source = connected(StartFrom.LAST_30_DAYS, false);
        account.record("of_1", "Weekly sync", RECENT, false);
        sync.sync(source.id());
        account.transcripts.put("of_1", account.transcripts.getOrDefault("of_1",
                new PlaudTeam.Transcript(null, java.util.List.of(new com.conxist.capture.domain.MeetingText.Line(
                        "Alex", 0, "Ready now")))));

        SyncResult second = sync.sync(source.id());
        SyncResult third = sync.sync(source.id());

        assertThat(second.imported()).isEqualTo(1);
        assertThat(third).isEqualTo(new SyncResult(0, 1, 0));
        assertThat(account.datesAsked).first()
                .isEqualTo(LocalDate.ofInstant(TestClock.NOW.minus(Duration.ofDays(30)), ZoneOffset.UTC).minusDays(1));
        assertThat(account.datesAsked).last()
                .isEqualTo(LocalDate.ofInstant(TestClock.NOW.minus(PlaudAccountConnection.LOOK_BACK), ZoneOffset.UTC)
                        .minusDays(1));
        assertThat(documents.saved.get(0).text()).doesNotContain("Notes of");
    }

    @Test
    void reads_every_page_of_the_recordings_list() {
        SourceSummary source = connected(StartFrom.LAST_30_DAYS, false);
        for (int i = 1; i <= 5; i++) {
            account.record("of_" + i, "Meeting " + i, RECENT.minus(Duration.ofHours(i)), true);
        }

        assertThat(sync.sync(source.id()).imported()).isEqualTo(5);
    }

    @Test
    void pauses_the_source_and_asks_to_sign_in_again_when_plaud_signed_conxist_out() {
        SourceSummary source = connected(StartFrom.LAST_30_DAYS, false);
        account.signedOut = true;

        assertThatThrownBy(() -> sync.sync(source.id()))
                .isInstanceOf(RuleViolation.class)
                .extracting(e -> ((RuleViolation) e).code()).isEqualTo("plaud.signed-out");
        assertThat(connections.bySource.get(source.id()).status()).isEqualTo(ConnectionStatus.PAUSED);
    }

    @Test
    void the_scheduled_check_includes_accounts_and_keeps_going_when_plaud_does_not_answer() {
        SourceSummary first = connected(StartFrom.LAST_30_DAYS, false);
        SourceSummary paused = connected(StartFrom.LAST_30_DAYS, false);
        connections.save(connections.bySource.get(paused.id()).paused(TestClock.NOW));
        account.record("of_1", "Weekly sync", RECENT, true);

        assertThat(sync.syncAll(FixedWorkspace.WORKSPACE_ID)).singleElement().satisfies(s -> {
            assertThat(s.sourceId()).isEqualTo(first.id());
            assertThat(s.result().imported()).isEqualTo(1);
        });
        account.down = true;
        assertThat(sync.syncAll(FixedWorkspace.WORKSPACE_ID)).singleElement()
                .satisfies(s -> assertThat(s.problemCode()).isEqualTo("plaud.unavailable"));
    }

    @Test
    void imports_picked_recordings_a_few_per_sync_and_counts_the_rest_as_queued() {
        SourceSummary source = connectedWithPicks(false, 12);

        SyncResult first = sync.sync(source.id());
        SyncResult second = sync.sync(source.id());

        assertThat(first).isEqualTo(new SyncResult(SyncPlaudAccount.PICKS_PER_SYNC, 0, 0, 2));
        assertThat(second).isEqualTo(new SyncResult(2, 0, 0, 0));
        assertThat(documents.saved).hasSize(12);
        assertThat(documents.saved).allSatisfy(d -> assertThat(d.title().value()).startsWith("Old meeting"));
        assertThat(picks.count(source.id())).isZero();
    }

    @Test
    void a_picked_recording_without_a_transcript_waits_and_one_deleted_in_plaud_is_dropped() {
        SourceSummary source = connectedWithPicks(false, 0);
        Instant old = TestClock.NOW.minus(Duration.ofDays(90));
        account.record("of_ready", "Ready", old, true);
        account.record("of_later", "Later", old.plus(Duration.ofHours(1)), false);
        picks.add(FixedWorkspace.WORKSPACE_ID, source.id(), java.util.List.of(
                new RecordingPicks.Pick("of_ready", old),
                new RecordingPicks.Pick("of_later", old.plus(Duration.ofHours(1))),
                new RecordingPicks.Pick("of_gone", old.plus(Duration.ofHours(2)))), TestClock.NOW);

        SyncResult result = sync.sync(source.id());

        assertThat(result).isEqualTo(new SyncResult(1, 0, 1, 0));
        assertThat(picks.next(source.id(), 10)).extracting(RecordingPicks.Pick::fileId).containsExactly("of_later");
        assertThat(picks.tried).containsKey(source.id() + "/of_later");
    }

    @Test
    void without_new_recordings_only_picks_are_imported() {
        SourceSummary source = connectedWithPicks(false, 0);
        account.record("of_new", "After connecting", TestClock.NOW.plus(Duration.ofHours(1)), true);

        assertThat(sync.sync(source.id())).isEqualTo(new SyncResult(0, 0, 0, 0));
        assertThat(documents.saved).isEmpty();
    }

    @Test
    void with_new_recordings_a_meeting_after_connecting_comes_in_but_an_older_one_not_picked_does_not() {
        SourceSummary source = connectedWithPicks(true, 0);
        account.record("of_new", "After connecting", TestClock.NOW.plus(Duration.ofHours(1)), true);
        account.record("of_old", "Before connecting", TestClock.NOW.minus(Duration.ofDays(3)), true);

        assertThat(sync.sync(source.id()).imported()).isEqualTo(1);
        assertThat(documents.saved).extracting(d -> d.originReference()).containsExactly("of_new");
    }

    @Test
    void gives_up_on_a_pick_still_waiting_for_its_transcript_after_a_week() {
        SourceSummary source = connectedWithPicks(false, 0);
        Instant old = TestClock.NOW.minus(Duration.ofDays(90));
        account.record("of_never", "Never transcribed", old, false);
        account.record("of_new_pick", "Picked today", old, false);
        Instant weekAgo = TestClock.NOW.minus(SyncPlaudAccount.GIVE_UP_AFTER).minusSeconds(1);
        picks.add(FixedWorkspace.WORKSPACE_ID, source.id(),
                java.util.List.of(new RecordingPicks.Pick("of_never", old)), weekAgo);
        picks.tried(source.id(), "of_never", weekAgo);
        picks.add(FixedWorkspace.WORKSPACE_ID, source.id(),
                java.util.List.of(new RecordingPicks.Pick("of_new_pick", old)), TestClock.NOW);
        picks.tried(source.id(), "of_new_pick", TestClock.NOW);

        SyncResult result = sync.sync(source.id());

        assertThat(result).isEqualTo(new SyncResult(0, 0, 1, 0));
        assertThat(picks.next(source.id(), 10)).extracting(RecordingPicks.Pick::fileId).containsExactly("of_new_pick");
    }

    /** A connection made by picking recordings: new ones start at connect time. */
    private SourceSummary connectedWithPicks(boolean importNew, int count) {
        SourceSummary source = sources.add(Provider.PLAUD, "alex@acme.test");
        connections.save(PlaudAccountConnection.connect(source.id(), FixedWorkspace.WORKSPACE_ID, "alex@acme.test",
                CaptureFakes.Account.keyOf("alex@acme.test"), importNew, false, TestClock.NOW));
        java.util.List<RecordingPicks.Pick> picked = new java.util.ArrayList<>();
        for (int i = 1; i <= count; i++) {
            Instant at = TestClock.NOW.minus(Duration.ofDays(60 + i));
            account.record("of_old_" + i, "Old meeting " + i, at, true);
            picked.add(new RecordingPicks.Pick("of_old_" + i, at));
        }
        picks.add(FixedWorkspace.WORKSPACE_ID, source.id(), picked, TestClock.NOW);
        return source;
    }

    /** A connection from before recordings were picked: it reaches back by date and imports new ones. */
    private SourceSummary connected(StartFrom startFrom, boolean notes) {
        SourceSummary source = sources.add(Provider.PLAUD, "alex@acme.test");
        connections.save(new PlaudAccountConnection(source.id(), FixedWorkspace.WORKSPACE_ID, "alex@acme.test",
                CaptureFakes.Account.keyOf("alex@acme.test"), startFrom, startFrom.cutoff(TestClock.NOW), notes, true,
                null, ConnectionStatus.ACTIVE, null, TestClock.NOW));
        return source;
    }
}
