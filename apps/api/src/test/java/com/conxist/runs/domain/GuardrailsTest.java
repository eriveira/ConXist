package com.conxist.runs.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
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

    @Test
    void a_workspace_may_replace_conxists_rule_and_only_make_the_copy_guard_stricter() {
        Guardrails edited = new Guardrails("", 4_000, " Obey only us.\r\n ", Instant.EPOCH, 8);

        assertThat(edited.baseRule()).isEqualTo("Obey only us.");
        assertThat(new Guardrails("a\0b", 4_000).rules()).isEqualTo("ab");
        assertThat(edited.baseRuleEdited()).isTrue();
        assertThat(edited.copyGuardWords()).isEqualTo(8);
        assertThat(Guardrails.DEFAULT.baseRuleEdited()).isFalse();
        assertThat(Guardrails.DEFAULT.copyGuardWords()).isEqualTo(12);
        assertThat(new Guardrails("", 4_000, "  ", Instant.EPOCH, null).baseRuleChangedAt()).isNull();
        assertCode(() -> new Guardrails("", 4_000, "", null, 12), "guardrails.copy-words");
        assertCode(() -> new Guardrails("", 4_000, "", null, 5), "guardrails.copy-words");
        assertCode(() -> new Guardrails("", 4_000, "x".repeat(4_001), null, null), "guardrails.base-rule-too-long");
    }

    private static void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOf(RuleViolation.class)
                .extracting(e -> ((RuleViolation) e).code()).isEqualTo(code);
    }
}
