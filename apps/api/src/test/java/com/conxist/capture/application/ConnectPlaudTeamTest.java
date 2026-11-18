package com.conxist.capture.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import com.conxist.capture.application.RecorderKeys.PublicKey;
import com.conxist.capture.domain.ConnectionStatus;
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

class ConnectPlaudTeamTest {

    private final CaptureFakes.Sources sources = new CaptureFakes.Sources();
    private final CaptureFakes.Keys keys = new CaptureFakes.Keys();
    private final CaptureFakes.Plaud plaud = new CaptureFakes.Plaud();
    private final CaptureFakes.Connections connections = new CaptureFakes.Connections();
    private final ConnectPlaudTeam connect = new ConnectPlaudTeam(sources, keys, plaud, connections,
            new CaptureContext(FixedWorkspace.standard(), new RecordedEvents(), new ImmediateUnitOfWork(),
                    TestClock.fixed()));

    @Test
    void a_tested_client_id_and_key_answer_with_the_team_name() {
        PublicKey key = connect.createKey();

        assertThat(connect.test("  client_acme \n", key.keyId()).teamName()).isEqualTo("Acme Engineering");
    }

    @Test
    void connecting_stores_the_link_with_the_chosen_start_and_never_returns_the_client_id() {
        PublicKey key = connect.createKey();
        SourceSummary source = sources.add(Provider.PLAUD, "Acme Engineering");

        ConnectionView view = connect.connect(source.id(), "client_acme", key.keyId(), StartFrom.LAST_7_DAYS, true);

        assertThat(view.teamName()).isEqualTo("Acme Engineering");
        assertThat(view.status()).isEqualTo(ConnectionStatus.ACTIVE);
        assertThat(connections.bySource.get(source.id())).satisfies(c -> {
            assertThat(c.clientId()).isEqualTo("client_acme");
            assertThat(c.cutoff()).isEqualTo(TestClock.NOW.minus(Duration.ofDays(7)));
            assertThat(c.checkpoint()).isNull();
        });
    }

    @Test
    void a_client_id_or_key_plaud_refuses_gets_one_clear_message() {
        PublicKey key = connect.createKey();

        assertThatThrownBy(() -> connect.test("client_other", key.keyId()))
                .isInstanceOf(RuleViolation.class)
                .extracting(e -> ((RuleViolation) e).code()).isEqualTo("plaud.not-accepted");
    }

    @Test
    void refuses_a_pasted_value_that_cannot_be_a_client_id_before_calling_plaud() {
        PublicKey key = connect.createKey();

        assertThatThrownBy(() -> connect.test("client acme; drop", key.keyId()))
                .extracting(e -> ((RuleViolation) e).code()).isEqualTo("plaud.client-id-invalid");
        assertThatThrownBy(() -> connect.test("", key.keyId()))
                .extracting(e -> ((RuleViolation) e).code()).isEqualTo("plaud.client-id-invalid");
    }

    @Test
    void refuses_an_unknown_key_a_second_connection_and_a_source_of_another_kind() {
        PublicKey key = connect.createKey();
        SourceSummary source = sources.add(Provider.PLAUD, "Team");
        SourceSummary uploads = sources.add(Provider.UPLOAD, "");
        connect.connect(source.id(), "client_acme", key.keyId(), StartFrom.NEW_ONLY, false);

        assertThatThrownBy(() -> connect.test("client_acme", "cx-ffffffffffffffff")).isInstanceOf(NotFound.class);
        assertThatThrownBy(() -> connect.connect(source.id(), "client_acme", key.keyId(), StartFrom.NEW_ONLY, false))
                .extracting(e -> ((RuleViolation) e).code()).isEqualTo("plaud.already-connected");
        assertThatThrownBy(() -> connect.connect(uploads.id(), "client_acme", key.keyId(), StartFrom.NEW_ONLY, false))
                .extracting(e -> ((RuleViolation) e).code()).isEqualTo("source.wrong-kind");
    }

    @Test
    void says_plaud_is_not_answering_when_it_is_down() {
        PublicKey key = connect.createKey();
        plaud.down = true;

        assertThatThrownBy(() -> connect.test("client_acme", key.keyId()))
                .extracting(e -> ((RuleViolation) e).code()).isEqualTo("plaud.unavailable");
    }
}
