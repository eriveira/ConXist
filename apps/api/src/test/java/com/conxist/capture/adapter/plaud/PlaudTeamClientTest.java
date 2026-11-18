package com.conxist.capture.adapter.plaud;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.conxist.capture.application.PlaudTeam;
import com.conxist.capture.application.PlaudTeam.ChangePage;
import com.conxist.capture.application.PlaudTeam.Credential;
import com.conxist.capture.domain.MeetingText.Line;
import com.conxist.testing.FakePlaudServer;
import com.conxist.testing.FakePlaudServer.Meeting;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class PlaudTeamClientTest {

    private static final Credential CREDENTIAL = new Credential(UUID.randomUUID(), "client_acme",
            "cx-00000000000000aa");

    private FakePlaudServer fake;
    private PlaudTeamClient client;

    @BeforeEach
    void start() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        fake = FakePlaudServer.strict();
        fake.register(CREDENTIAL.clientId(), JdbcRecorderKeys.pem(pair.getPublic().getEncoded()));
        client = new PlaudTeamClient(RestClient.create(), fake.apiBase(), fake.tokenUrl(), FakePlaudServer.AUDIENCE,
                2, (workspaceId, keyId) -> pair.getPrivate(), Clock.systemUTC());
    }

    @AfterEach
    void stop() {
        fake.close();
    }

    @Test
    void signs_in_with_a_signed_assertion_and_reads_the_team_name() {
        assertThat(client.teamName(CREDENTIAL)).isEqualTo(FakePlaudServer.TEAM_NAME);
    }

    @Test
    void a_client_id_plaud_does_not_know_is_access_denied() {
        assertThatThrownBy(() -> client.teamName(new Credential(CREDENTIAL.workspaceId(), "client_other",
                CREDENTIAL.keyId())))
                .isInstanceOf(PlaudTeam.AccessDenied.class);
    }

    @Test
    void a_key_that_was_not_registered_for_the_client_is_access_denied() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair other = generator.generateKeyPair();
        PlaudTeamClient wrongKey = new PlaudTeamClient(RestClient.create(), fake.apiBase(), fake.tokenUrl(),
                FakePlaudServer.AUDIENCE, 2, (workspaceId, keyId) -> other.getPrivate(), Clock.systemUTC());

        assertThatThrownBy(() -> wrongKey.teamName(CREDENTIAL)).isInstanceOf(PlaudTeam.AccessDenied.class);
    }

    @Test
    void pages_through_the_change_feed_and_maps_each_file() {
        Instant recorded = Instant.parse("2026-11-16T15:00:00Z");
        fake.add(new Meeting("f1", "One", recorded, true, "Short summary", List.of("Ana: Hello")));
        fake.add(new Meeting("f2", "Two", recorded, false, "", List.of()));
        fake.add(new Meeting("f3", "Three", recorded, true, "", List.of("Ben: Bye")));

        ChangePage first = client.changes(CREDENTIAL, null, null);
        ChangePage second = client.changes(CREDENTIAL, null, first.nextCursor());

        assertThat(first.hasMore()).isTrue();
        assertThat(first.nextCheckpoint()).isNull();
        assertThat(first.files()).extracting(PlaudTeam.TeamFile::id).containsExactly("f1", "f2");
        assertThat(first.files().getFirst()).satisfies(file -> {
            assertThat(file.recordedAt()).isEqualTo(recorded);
            assertThat(file.hasTranscript()).isTrue();
            assertThat(file.hasSummary()).isTrue();
            assertThat(file.deleted()).isFalse();
        });
        assertThat(second.hasMore()).isFalse();
        assertThat(second.nextCheckpoint()).isEqualTo("3");
        assertThat(client.changes(CREDENTIAL, "3", null).files()).isEmpty();
    }

    @Test
    void reads_a_transcript_and_summaries_and_treats_a_vanished_file_as_empty() {
        fake.add(new Meeting("f1", "One", Instant.now(), true, "Short summary", List.of("Ana: Hello", "Ben: Hi")));

        assertThat(client.transcript(CREDENTIAL, "f1")).hasValueSatisfying(t -> {
            assertThat(t.language()).isEqualTo("en");
            assertThat(t.lines()).containsExactly(new Line("Ana", 0, "Hello"), new Line("Ben", 15_000, "Hi"));
        });
        assertThat(client.summaries(CREDENTIAL, "f1")).containsExactly("Short summary");
        assertThat(client.transcript(CREDENTIAL, "missing")).isEmpty();
    }

    @Test
    void when_plaud_revokes_access_the_next_call_is_access_denied() {
        client.teamName(CREDENTIAL);
        fake.revokeAll();

        assertThatThrownBy(() -> client.changes(CREDENTIAL, null, null)).isInstanceOf(PlaudTeam.AccessDenied.class);
    }

    @Test
    void an_unreachable_plaud_is_unavailable() {
        fake.close();

        assertThatThrownBy(() -> client.teamName(CREDENTIAL)).isInstanceOf(PlaudTeam.Unavailable.class);
    }
}
