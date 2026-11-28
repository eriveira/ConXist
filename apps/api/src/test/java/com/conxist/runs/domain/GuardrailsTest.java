package com.conxist.runs.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.conxist.platform.RuleViolation;
import org.junit.jupiter.api.Test;

class GuardrailsTest {

    @Test
    void rules_are_kept_trimmed_with_plain_line_ends_and_the_limit_can_only_go_lower() {
        assertThat(new Guardrails(" a\r\nb ", 200).rules()).isEqualTo("a\nb");
        assertThat(new Guardrails(null, 4_000).rules()).isEmpty();
        assertThat(Guardrails.DEFAULT.maxInputCharacters()).isEqualTo(4_000);
        assertCode(() -> new Guardrails("", 199), "guardrails.input-limit");
        assertCode(() -> new Guardrails("", 4_001), "guardrails.input-limit");
        assertCode(() -> new Guardrails("x".repeat(2_001), 4_000), "guardrails.rules-too-long");
    }

    private static void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOf(RuleViolation.class)
                .extracting(e -> ((RuleViolation) e).code()).isEqualTo(code);
    }
}
