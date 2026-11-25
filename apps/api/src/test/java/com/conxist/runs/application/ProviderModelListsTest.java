package com.conxist.runs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import com.conxist.testing.TestClock;
import org.junit.jupiter.api.Test;

class ProviderModelListsTest {

    private final TestClock.Moving clock = TestClock.moving();
    private final TestPort anthropic = new TestPort();
    private final ProviderModelLists lists = new ProviderModelLists(Duration.ofHours(1), Duration.ofSeconds(30),
            Duration.ofSeconds(5), clock);

    @Test
    void a_refresh_asks_again_only_once_the_kept_list_is_older_than_the_minimum() {
        lists.models(anthropic, false);
        lists.models(anthropic, true);
        assertThat(anthropic.asked).isEqualTo(1);

        clock.advance(Duration.ofSeconds(31));
        lists.models(anthropic, true);
        assertThat(anthropic.asked).isEqualTo(2);
    }

    @Test
    void a_failed_ask_keeps_the_last_list_with_a_reason_code() {
        lists.models(anthropic, false);
        clock.advance(Duration.ofHours(2));
        anthropic.failWith = "down";

        ProviderModelLists.Listing listing = lists.models(anthropic, false);
        assertThat(listing.models()).hasSize(4);
        assertThat(listing.error()).isEqualTo(ProviderModelLists.ListError.FAILED);
    }

    @Test
    void a_model_id_must_be_short_and_plain_and_a_label_loses_control_characters() {
        assertThat(ProviderModel.validId("claude-opus-5-5")).isTrue();
        assertThat(ProviderModel.validId("a".repeat(101))).isFalse();
        assertThat(ProviderModel.validId("claude opus")).isFalse();
        assertThat(ProviderModel.validId("<b>")).isFalse();
        assertThatThrownBy(() -> new ProviderModel("x y", "X")).isInstanceOf(IllegalArgumentException.class);
        assertThat(new ProviderModel("m", "Claude\u0000 Opus\n").label()).isEqualTo("Claude  Opus");
        assertThat(new ProviderModel("m", " ").label()).isEqualTo("m");
    }
}
