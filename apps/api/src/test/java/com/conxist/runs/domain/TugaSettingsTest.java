package com.conxist.runs.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import com.conxist.platform.RuleViolation;
import org.junit.jupiter.api.Test;

class TugaSettingsTest {

    private static final Instant MONDAY = Instant.parse("2026-11-23T10:00:00Z");
    private static final Instant TUESDAY = Instant.parse("2026-11-24T10:00:00Z");

    @Test
    void a_model_is_picked_whole_with_a_known_effort() {
        assertCode(() -> new TugaSettings("anthropic", null, "LOW", "", null), "tuga.model-incomplete");
        assertCode(() -> new TugaSettings("anthropic", "claude-sonnet-5-5", "MAX", "", null), "model.unknown-effort");
        assertThat(new TugaSettings("anthropic", "claude-sonnet-5-5", "LOW", "", null).modelPicked()).isTrue();
        assertThat(TugaSettings.DEFAULT.modelPicked()).isFalse();
    }

    @Test
    void conxists_prompt_saved_as_it_is_counts_as_conxists_and_a_long_one_is_refused() {
        assertThat(new TugaSettings(null, null, null, "  " + TugaPrompt.DEFAULT_PROMPT + "\r\n", MONDAY))
                .satisfies(settings -> {
                    assertThat(settings.promptEdited()).isFalse();
                    assertThat(settings.promptChangedAt()).isNull();
                });
        assertThat(new TugaSettings(null, null, null, "😀".repeat(TugaPrompt.MAX_PROMPT), null).promptEdited()).isTrue();
        assertCode(() -> new TugaSettings(null, null, null, "a".repeat(TugaPrompt.MAX_PROMPT + 1), null),
                "tuga.prompt-too-long");
    }

    @Test
    void the_prompts_date_moves_only_when_its_text_changes() {
        TugaSettings before = new TugaSettings(null, null, null, "Answer in Spanish.", MONDAY);

        assertThat(new TugaSettings("anthropic", "claude-sonnet-5-5", "LOW", "Answer in Spanish.", null)
                .savedOver(before, TUESDAY).promptChangedAt()).isEqualTo(MONDAY);
        assertThat(new TugaSettings(null, null, null, "Answer in French.", null).savedOver(before, TUESDAY)
                .promptChangedAt()).isEqualTo(TUESDAY);
        assertThat(new TugaSettings(null, null, null, "", null).savedOver(before, TUESDAY).promptChangedAt()).isNull();
    }

    private static void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOf(RuleViolation.class).extracting("code").isEqualTo(code);
    }
}
