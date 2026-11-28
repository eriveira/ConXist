package com.conxist.runs.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.conxist.platform.RuleViolation;
import org.junit.jupiter.api.Test;

class RunLimitsTest {

    @Test
    void limits_take_the_edges_of_their_bounds() {
        assertThat(new RunLimits(1, 10, 0)).isNotNull();
        assertThat(new RunLimits(20, 600, 50)).isNotNull();
        assertThat(RunLimits.DEFAULT).isEqualTo(new RunLimits(6, 120, 10));
    }

    @Test
    void a_limit_out_of_bounds_names_what_is_wrong() {
        assertCode(() -> new RunLimits(0, 120, 10), "run-limits.steps");
        assertCode(() -> new RunLimits(21, 120, 10), "run-limits.steps");
        assertCode(() -> new RunLimits(6, 9, 10), "run-limits.time");
        assertCode(() -> new RunLimits(6, 601, 10), "run-limits.time");
        assertCode(() -> new RunLimits(6, 120, -1), "run-limits.tool-calls");
        assertCode(() -> new RunLimits(6, 120, 51), "run-limits.tool-calls");
    }

    private static void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(RuleViolation.class,
                violation -> assertThat(violation.code()).isEqualTo(code));
    }
}
