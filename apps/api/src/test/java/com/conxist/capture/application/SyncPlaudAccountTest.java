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
    private final SyncRecorder sync = new SyncRecorder(sources, new CaptureFakes.Plaud(), new CaptureFakes.Connections(),
            account, connections, documents,
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

        assertThat(sync.syncAll()).singleElement().satisfies(s -> {
            assertThat(s.sourceId()).isEqualTo(first.id());
            assertThat(s.result().imported()).isEqualTo(1);
        });
        account.down = true;
        assertThat(sync.syncAll()).singleElement()
                .satisfies(s -> assertThat(s.problemCode()).isEqualTo("plaud.unavailable"));
    }

    private SourceSummary connected(StartFrom startFrom, boolean notes) {
        SourceSummary source = sources.add(Provider.PLAUD, "alex@acme.test");
        connections.save(PlaudAccountConnection.connect(source.id(), FixedWorkspace.WORKSPACE_ID, "alex@acme.test",
                CaptureFakes.Account.keyOf("alex@acme.test"),
                startFrom, notes, TestClock.NOW));
        return source;
    }
}
