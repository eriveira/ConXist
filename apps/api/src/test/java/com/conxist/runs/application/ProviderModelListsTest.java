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
        lists.models(anthropic, TestModels.COMPUTER_KEY, false);
        lists.models(anthropic, TestModels.COMPUTER_KEY, true);
        assertThat(anthropic.asked).isEqualTo(1);

        clock.advance(Duration.ofSeconds(31));
        lists.models(anthropic, TestModels.COMPUTER_KEY, true);
        assertThat(anthropic.asked).isEqualTo(2);
    }

    @Test
    void a_failed_ask_keeps_the_last_list_with_a_reason_code() {
        lists.models(anthropic, TestModels.COMPUTER_KEY, false);
        clock.advance(Duration.ofHours(2));
        anthropic.failWith = "down";

        ProviderModelLists.Listing listing = lists.models(anthropic, TestModels.COMPUTER_KEY, false);
        assertThat(listing.models()).hasSize(4);
        assertThat(listing.error()).isEqualTo(ProviderModelLists.ListError.FAILED);
    }

    @Test
    void a_check_always_asks_and_a_refused_key_loses_its_kept_list() {
        lists.models(anthropic, TestModels.COMPUTER_KEY, false);
        lists.check(anthropic, TestModels.COMPUTER_KEY);
        assertThat(anthropic.asked).isEqualTo(2);

        anthropic.refuses = TestModels.COMPUTER_KEY;
        ProviderModelLists.Listing refused = lists.check(anthropic, TestModels.COMPUTER_KEY);
        assertThat(refused.error()).isEqualTo(ProviderModelLists.ListError.KEY_REJECTED);
        assertThat(refused.models()).isEmpty();
        assertThat(refused.answered()).isTrue();

        anthropic.refuses = null;
        assertThat(lists.models(anthropic, TestModels.COMPUTER_KEY, false).asked()).isTrue();
    }

    @Test
    void each_key_has_its_own_list() {
        lists.models(anthropic, TestModels.COMPUTER_KEY, false);
        lists.models(anthropic, new ApiKey("sk-ant-api03-another-workspace-key"), false);
        assertThat(anthropic.asked).isEqualTo(2);
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
