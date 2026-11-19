package com.conxist.capture.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import com.conxist.capture.DocumentOrigin;
import com.conxist.capture.application.PlaudAccounts.SignInStart;
import com.conxist.capture.application.PlaudAccounts.SignInStatus;
import com.conxist.capture.application.SignInToPlaud.AccountChoice;
import com.conxist.capture.domain.CapturedDocument;
import com.conxist.capture.domain.ConnectionStatus;
import com.conxist.capture.domain.DocumentTitle;
import com.conxist.capture.domain.PlaudAccountConnection;
import com.conxist.platform.NotFound;
import com.conxist.platform.RuleViolation;
import com.conxist.sources.Provider;
import com.conxist.sources.SourceSummary;
import com.conxist.testing.FixedWorkspace;
import com.conxist.testing.ImmediateUnitOfWork;
import com.conxist.testing.RecordedEvents;
import com.conxist.testing.TestClock;
import org.junit.jupiter.api.Test;

class SignInToPlaudTest {

    private final CaptureFakes.Sources sources = new CaptureFakes.Sources();
    private final CaptureFakes.Account account = new CaptureFakes.Account();
    private final CaptureFakes.AccountConnections connections = new CaptureFakes.AccountConnections();
    private final CaptureFakes.Connections teams = new CaptureFakes.Connections();
    private final CaptureFakes.Picks picks = new CaptureFakes.Picks();
    private final CaptureFakes.Documents documents = new CaptureFakes.Documents();
    private final TestClock.Moving clock = TestClock.moving();
    private final CaptureContext context = new CaptureContext(FixedWorkspace.standard(), new RecordedEvents(),
            new ImmediateUnitOfWork(), clock);
    private final ListPlaudRecordings recordings = new ListPlaudRecordings(account, documents, context);
    private final SignInToPlaud signIn = new SignInToPlaud(sources, account, connections, teams, recordings, picks,
            context);

    @Test
    void a_sign_in_waits_until_plaud_sends_the_person_back_and_then_names_the_account() {
        SignInStart start = signIn.start();
        assertThat(start.authorizationUrl()).hasHost("plaud.test");
        assertThat(signIn.status(start.signInId()).status()).isEqualTo(SignInStatus.WAITING);

        signIn.finish(start.signInId().toString(), "code", null);

        assertThat(signIn.status(start.signInId())).satisfies(s -> {
            assertThat(s.status()).isEqualTo(SignInStatus.SIGNED_IN);
            assertThat(s.accountLabel()).isEqualTo("alex@acme.test");
        });
    }

    @Test
    void connecting_keeps_the_picked_recordings_for_the_sync_and_new_ones_start_now() {
        SourceSummary source = sources.add(Provider.PLAUD, "alex@acme.test");
        UUID signInId = account.signedIn("alex@acme.test");
        Instant old = TestClock.NOW.minus(Duration.ofDays(40));
        account.record("of_1", "Kick-off", old, true);
        account.record("of_2", "Retro", old.plus(Duration.ofDays(1)), true);
        assertThat(recordings.list(signInId, null, null).recordings()).extracting(ListPlaudRecordings.Recording::id)
                .containsExactly("of_2", "of_1");

        ConnectionView view = signIn.connect(source.id(), new AccountChoice(signInId, List.of("of_1"), true, false));

        assertThat(view.kind()).isEqualTo(ConnectionView.Kind.ACCOUNT);
        assertThat(view.label()).isEqualTo("alex@acme.test");
        assertThat(account.attached).containsEntry(source.id(), signInId);
        assertThat(connections.bySource.get(source.id())).satisfies(c -> {
            assertThat(c.cutoff()).isEqualTo(TestClock.NOW);
            assertThat(c.importNew()).isTrue();
        });
        assertThat(picks.next(source.id(), 10)).containsExactly(new RecordingPicks.Pick("of_1", old));
    }

    @Test
    void refuses_a_pick_that_is_not_in_the_account_and_an_empty_choice() {
        SourceSummary source = sources.add(Provider.PLAUD, "");
        UUID signInId = account.signedIn("alex@acme.test");
        account.record("of_1", "Kick-off", TestClock.NOW.minus(Duration.ofDays(2)), true);

        assertThatThrownBy(() -> signIn.connect(source.id(), new AccountChoice(signInId, List.of("of_guessed"), true, false)))
                .isInstanceOf(RuleViolation.class)
                .extracting(e -> ((RuleViolation) e).code()).isEqualTo("plaud.pick-not-listed");
        assertThatThrownBy(() -> signIn.connect(source.id(), new AccountChoice(signInId, List.of(), false, false)))
                .isInstanceOf(RuleViolation.class)
                .extracting(e -> ((RuleViolation) e).code()).isEqualTo("plaud.nothing-picked");
        assertThat(connections.bySource).isEmpty();
        assertThat(account.attached).isEmpty();
    }

    @Test
    void the_list_is_kept_for_a_few_minutes_and_dates_must_be_in_order() {
        UUID signInId = account.signedIn("alex@acme.test");
        account.record("of_1", "Kick-off", TestClock.NOW.minus(Duration.ofDays(2)), true);

        recordings.list(signInId, null, null);
        recordings.list(signInId, null, null);
        assertThat(account.listCalls).isEqualTo(1);
        clock.advance(ListPlaudRecordings.KEEP.plusSeconds(1));
        recordings.list(signInId, null, null);
        assertThat(account.listCalls).isEqualTo(2);

        assertThatThrownBy(() -> recordings.list(signInId, LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 1)))
                .extracting(e -> ((RuleViolation) e).code()).isEqualTo("plaud.dates-reversed");
        assertThatThrownBy(() -> recordings.list(UUID.randomUUID(), null, null)).isInstanceOf(NotFound.class);
    }

    @Test
    void a_pick_from_a_dated_list_is_accepted_after_the_list_expired() {
        SourceSummary source = sources.add(Provider.PLAUD, "alex@acme.test");
        UUID signInId = account.signedIn("alex@acme.test");
        Instant old = TestClock.NOW.minus(Duration.ofDays(400));
        account.record("of_old", "Older than the newest 500", old, true);
        for (int i = 0; i < PlaudAccounts.MAX_RECENT_FILES; i++) {
            account.record("of_" + i, "Recent " + i, TestClock.NOW.minus(Duration.ofHours(i + 1)), true);
        }
        LocalDate day = LocalDate.ofInstant(old, java.time.ZoneOffset.UTC);
        assertThat(recordings.list(signInId, null, null).capped()).isTrue();
        assertThat(recordings.list(signInId, day, day).recordings()).extracting(ListPlaudRecordings.Recording::id)
                .containsExactly("of_old");
        clock.advance(ListPlaudRecordings.KEEP.plusSeconds(1));

        signIn.connect(source.id(), new AccountChoice(signInId, List.of("of_old"), false, false));

        assertThat(picks.next(source.id(), 10)).containsExactly(new RecordingPicks.Pick("of_old", old));
    }

    @Test
    void the_list_says_which_recordings_this_workspace_already_holds_or_deleted() {
        UUID signInId = account.signedIn("alex@acme.test");
        account.record("of_new", "New", TestClock.NOW.minus(Duration.ofDays(1)), true);
        account.record("of_held", "Held", TestClock.NOW.minus(Duration.ofDays(2)), true);
        account.record("of_gone", "Deleted", TestClock.NOW.minus(Duration.ofDays(3)), true);
        hold("of_held");
        documents.keptOut.add(UUID.randomUUID() + "/of_gone");

        assertThat(recordings.list(signInId, null, null).recordings())
                .extracting(ListPlaudRecordings.Recording::id, ListPlaudRecordings.Recording::inConxist)
                .containsExactly(tuple("of_new", null), tuple("of_held", CapturedDocumentRepository.Known.HELD),
                        tuple("of_gone", CapturedDocumentRepository.Known.DELETED));
    }

    @Test
    void refuses_to_pick_a_recording_this_workspace_already_holds() {
        SourceSummary source = sources.add(Provider.PLAUD, "alex@acme.test");
        UUID signInId = account.signedIn("alex@acme.test");
        account.record("of_held", "Held", TestClock.NOW.minus(Duration.ofDays(2)), true);
        hold("of_held");
        recordings.list(signInId, null, null);

        assertThatThrownBy(() -> signIn.connect(source.id(), new AccountChoice(signInId, List.of("of_held"), true, false)))
                .isInstanceOf(RuleViolation.class)
                .extracting(e -> ((RuleViolation) e).code()).isEqualTo("plaud.pick-already-held");
        assertThat(connections.bySource).isEmpty();
    }

    private void hold(String fileId) {
        documents.saved.add(new CapturedDocument(UUID.randomUUID(), FixedWorkspace.WORKSPACE_ID, UUID.randomUUID(),
                new DocumentTitle("Held"), "text/markdown", 1, "x", DocumentOrigin.RECORDING, fileId, TestClock.NOW));
    }

    @Test
    void a_recording_without_a_readable_date_is_not_listed() {
        UUID signInId = account.signedIn("alex@acme.test");
        account.record("of_1", "Kick-off", TestClock.NOW.minus(Duration.ofDays(2)), true);
        account.record("of_undated", "No date", null, true);

        assertThat(recordings.list(signInId, null, null).recordings()).extracting(ListPlaudRecordings.Recording::id)
                .containsExactly("of_1");
    }

    @Test
    void refuses_to_connect_before_the_sign_in_finished_or_twice() {
        SourceSummary source = sources.add(Provider.PLAUD, "");
        SignInStart waiting = signIn.start();

        assertThatThrownBy(() -> signIn.connect(source.id(), new AccountChoice(waiting.signInId(), List.of(), true, true)))
                .isInstanceOf(RuleViolation.class)
                .extracting(e -> ((RuleViolation) e).code()).isEqualTo("plaud.sign-in-not-finished");
        assertThatThrownBy(() -> signIn.status(UUID.randomUUID())).isInstanceOf(NotFound.class);

        signIn.connect(source.id(), new AccountChoice(account.signedIn("a@b.test"), List.of(), true, true));
        assertThatThrownBy(() -> signIn.connect(source.id(), new AccountChoice(account.signedIn("a@b.test"), List.of(), true, true)))
                .isInstanceOf(RuleViolation.class)
                .extracting(e -> ((RuleViolation) e).code()).isEqualTo("plaud.already-connected");
    }

    @Test
    void a_cancelled_sign_in_says_so() {
        SignInStart start = signIn.start();

        signIn.finish(start.signInId().toString(), null, "access_denied");

        assertThat(signIn.status(start.signInId()).status()).isEqualTo(SignInStatus.CANCELLED);
    }

    @Test
    void signing_in_again_brings_a_paused_account_back() {
        SourceSummary source = sources.add(Provider.PLAUD, "");
        connections.save(PlaudAccountConnection.connect(source.id(), FixedWorkspace.WORKSPACE_ID, "alex@acme.test",
                CaptureFakes.Account.keyOf("alex@acme.test"),
                true, true, TestClock.NOW).paused(TestClock.NOW));

        ConnectionView view = signIn.reconnect(source.id(), account.signedIn("alex@acme.test"));

        assertThat(view.status()).isEqualTo(ConnectionStatus.ACTIVE);
        assertThat(connections.bySource.get(source.id()).status()).isEqualTo(ConnectionStatus.ACTIVE);
    }

    @Test
    void signing_in_again_with_another_account_is_refused_and_the_source_stays_paused() {
        SourceSummary source = sources.add(Provider.PLAUD, "");
        connections.save(PlaudAccountConnection.connect(source.id(), FixedWorkspace.WORKSPACE_ID, "alex@acme.test",
                CaptureFakes.Account.keyOf("alex@acme.test"),
                true, true, TestClock.NOW).paused(TestClock.NOW));

        assertThatThrownBy(() -> signIn.reconnect(source.id(), account.signedIn("alex@acme.test", "key:someone-else")))
                .hasMessageContaining("different Plaud account");
        assertThat(connections.bySource.get(source.id()).status()).isEqualTo(ConnectionStatus.PAUSED);
    }

    @Test
    void says_plaud_is_not_answering_when_a_sign_in_cannot_start() {
        account.down = true;

        assertThatThrownBy(signIn::start)
                .isInstanceOf(RuleViolation.class)
                .extracting(e -> ((RuleViolation) e).code()).isEqualTo("plaud.unavailable");
    }
}
