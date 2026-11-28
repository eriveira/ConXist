package com.conxist.runs.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CopyGuardTest {

    private static final String BOARD = "The board agreed to cut the marketing budget by forty percent next quarter and"
            + " to keep the plan secret from the sales team until March.";
    private static final String HANDED = "Ana said guest checkout is broken on Safari when the cart holds more than"
            + " ten items and the coupon field is left empty.";

    private final CopyGuard guard = new CopyGuard(HANDED);

    @Test
    void a_passage_of_something_the_run_read_is_refused_whatever_its_case_and_punctuation() {
        guard.read(BOARD, "Board meeting, 20 Nov");

        String copied = "Summary: THE BOARD agreed, to cut the marketing budget by forty-percent next quarter!";

        assertThat(guard.check(Map.of("fields", Map.of("description", List.of(copied))), false)).hasValueSatisfying(
                refusal -> {
                    assertThat(refusal.reason()).isEqualTo(CopyGuard.Refusal.Reason.COPIES_SOURCE);
                    assertThat(refusal.from()).isEqualTo("Board meeting, 20 Nov");
                });
    }

    @Test
    void the_handed_meeting_may_be_quoted_and_short_or_own_words_go() {
        guard.read(HANDED + " " + BOARD, "Weekly sync");

        assertThat(guard.check(Map.of("title", "Safari checkout", "body", HANDED), true)).isEmpty();
        assertThat(guard.check(Map.of("body", "The board agreed to cut the marketing budget"), true)).isEmpty();
        assertThat(guard.check(Map.of("query", "marketing budget forty percent"), false)).isEmpty();
    }

    @Test
    void a_change_with_more_text_than_the_limit_is_refused_but_a_read_is_only_checked_for_copies() {
        String own = "word ".repeat(CopyGuard.MAX_CHANGE_CHARACTERS / 5 + 1);

        assertThat(guard.check(Map.of("body", own), true)).hasValueSatisfying(
                refusal -> assertThat(refusal.reason()).isEqualTo(CopyGuard.Refusal.Reason.TOO_LONG));
        assertThat(guard.check(Map.of("body", own), false)).isEmpty();
        assertThat(guard.check(Map.of("count", 3, "ids", List.of(1, 2)), true)).isEmpty();
    }

    @Test
    void a_text_shorter_than_one_passage_gives_no_passages() {
        assertThat(CopyGuard.shingles("one two three")).isEmpty();
        assertThat(CopyGuard.shingles(null)).isEmpty();
        assertThat(CopyGuard.shingles("a b c d e f g h i j k l m")).hasSize(2);
    }
}
