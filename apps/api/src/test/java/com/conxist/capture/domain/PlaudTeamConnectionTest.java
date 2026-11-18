package com.conxist.capture.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlaudTeamConnectionTest {

    private static final Instant NOW = Instant.parse("2026-11-18T12:00:00Z");

    @Test
    void the_chosen_start_sets_which_recordings_are_in_range() {
        PlaudTeamConnection lastWeek = connect(StartFrom.LAST_7_DAYS);
        PlaudTeamConnection newOnly = connect(StartFrom.NEW_ONLY);

        assertThat(lastWeek.wasRecordedInRange(NOW.minus(Duration.ofDays(6)))).isTrue();
        assertThat(lastWeek.wasRecordedInRange(NOW.minus(Duration.ofDays(8)))).isFalse();
        assertThat(newOnly.wasRecordedInRange(NOW.minusSeconds(1))).isFalse();
        assertThat(newOnly.wasRecordedInRange(NOW)).isTrue();
        assertThat(newOnly.wasRecordedInRange(null)).isFalse();
    }

    @Test
    void a_paused_connection_becomes_active_again_after_a_complete_check() {
        PlaudTeamConnection paused = connect(StartFrom.LAST_30_DAYS).paused(NOW);

        PlaudTeamConnection checked = paused.checked("cp-1", NOW.plusSeconds(60));

        assertThat(paused.status()).isEqualTo(ConnectionStatus.PAUSED);
        assertThat(checked.status()).isEqualTo(ConnectionStatus.ACTIVE);
        assertThat(checked.checkpoint()).isEqualTo("cp-1");
        assertThat(checked.withoutCheckpoint().checkpoint()).isNull();
    }

    @Test
    void cleans_a_pasted_client_id_and_rejects_anything_else() {
        assertThat(PlaudTeamConnection.cleanClientId(" client_ab-12.x:y \t")).isEqualTo("client_ab-12.x:y");
        assertThatThrownBy(() -> PlaudTeamConnection.cleanClientId("a b")).isInstanceOf(InvalidRecorderLink.class);
        assertThatThrownBy(() -> PlaudTeamConnection.cleanClientId("x".repeat(201))).isInstanceOf(InvalidRecorderLink.class);
        assertThatThrownBy(() -> PlaudTeamConnection.cleanClientId(null)).isInstanceOf(InvalidRecorderLink.class);
    }

    @Test
    void keeps_a_long_team_name_within_the_limit() {
        PlaudTeamConnection connection = PlaudTeamConnection.connect(UUID.randomUUID(), UUID.randomUUID(), "client_1",
                "cx-1", "  " + "T".repeat(300), StartFrom.NEW_ONLY, false, NOW);

        assertThat(connection.teamName()).hasSize(PlaudTeamConnection.MAX_TEAM_NAME_LENGTH);
    }

    private static PlaudTeamConnection connect(StartFrom startFrom) {
        return PlaudTeamConnection.connect(UUID.randomUUID(), UUID.randomUUID(), "client_1", "cx-1", "Team",
                startFrom, false, NOW);
    }
}
