package com.conxist.capture.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.UUID;
import com.conxist.capture.application.PlaudAccounts.SignInStart;
import com.conxist.capture.application.PlaudAccounts.SignInStatus;
import com.conxist.capture.domain.ConnectionStatus;
import com.conxist.capture.domain.PlaudAccountConnection;
import com.conxist.capture.domain.StartFrom;
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
    private final SignInToPlaud signIn = new SignInToPlaud(sources, account, connections, teams,
            new CaptureContext(FixedWorkspace.standard(), new RecordedEvents(), new ImmediateUnitOfWork(),
                    TestClock.fixed()));

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
    void connecting_links_the_signed_in_account_to_the_source_with_the_chosen_start() {
        SourceSummary source = sources.add(Provider.PLAUD, "alex@acme.test");
        UUID signInId = account.signedIn("alex@acme.test");

        ConnectionView view = signIn.connect(source.id(), signInId, StartFrom.LAST_7_DAYS, false);

        assertThat(view.kind()).isEqualTo(ConnectionView.Kind.ACCOUNT);
        assertThat(view.label()).isEqualTo("alex@acme.test");
        assertThat(account.attached).containsEntry(source.id(), signInId);
        assertThat(connections.bySource.get(source.id()).cutoff()).isEqualTo(TestClock.NOW.minus(Duration.ofDays(7)));
    }

    @Test
    void refuses_to_connect_before_the_sign_in_finished_or_twice() {
        SourceSummary source = sources.add(Provider.PLAUD, "");
        SignInStart waiting = signIn.start();

        assertThatThrownBy(() -> signIn.connect(source.id(), waiting.signInId(), StartFrom.NEW_ONLY, true))
                .isInstanceOf(RuleViolation.class)
                .extracting(e -> ((RuleViolation) e).code()).isEqualTo("plaud.sign-in-not-finished");
        assertThatThrownBy(() -> signIn.status(UUID.randomUUID())).isInstanceOf(NotFound.class);

        signIn.connect(source.id(), account.signedIn("a@b.test"), StartFrom.NEW_ONLY, true);
        assertThatThrownBy(() -> signIn.connect(source.id(), account.signedIn("a@b.test"), StartFrom.NEW_ONLY, true))
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
                StartFrom.LAST_30_DAYS, true, TestClock.NOW).paused(TestClock.NOW));

        ConnectionView view = signIn.reconnect(source.id(), account.signedIn("alex@acme.test"));

        assertThat(view.status()).isEqualTo(ConnectionStatus.ACTIVE);
        assertThat(connections.bySource.get(source.id()).status()).isEqualTo(ConnectionStatus.ACTIVE);
    }

    @Test
    void signing_in_again_with_another_account_is_refused_and_the_source_stays_paused() {
        SourceSummary source = sources.add(Provider.PLAUD, "");
        connections.save(PlaudAccountConnection.connect(source.id(), FixedWorkspace.WORKSPACE_ID, "alex@acme.test",
                CaptureFakes.Account.keyOf("alex@acme.test"),
                StartFrom.LAST_30_DAYS, true, TestClock.NOW).paused(TestClock.NOW));

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
