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
    private static final String COPY = "the board agreed to cut the marketing budget by forty percent next quarter";

    private final CopyGuard guard = new CopyGuard(HANDED);

    CopyGuardTest() {
        guard.read(BOARD, "Board meeting, 20 Nov");
    }

    @Test
    void a_passage_of_something_the_run_read_is_refused_whatever_its_case_and_punctuation() {
        String copied = "Summary: THE BOARD agreed, to cut the marketing budget by forty-percent next quarter!";

        assertThat(guard.copiedFrom(Map.of("fields", Map.of("description", List.of(copied)))))
                .contains("Board meeting, 20 Nov");
    }

    @Test
    void a_passage_split_over_fields_list_items_or_keys_is_still_refused() {
        assertThat(guard.copiedFrom(Map.of("lines", List.of("The board agreed to cut the", "marketing budget by forty",
                "percent next quarter")))).isPresent();
        assertThat(guard.copiedFrom(Map.of("labels", Map.of(COPY, true)))).isPresent();
    }

    @Test
    void numbered_words_invisible_characters_and_other_forms_of_letters_do_not_hide_a_passage() {
        StringBuilder numbered = new StringBuilder();
        String[] words = COPY.split(" ");
        for (int i = 0; i < words.length; i++) {
            numbered.append(i + 1).append(". ").append(words[i]).append(' ');
        }
        assertThat(guard.copiedFrom(Map.of("body", numbered.toString()))).isPresent();
        assertThat(guard.copiedFrom(Map.of("body", COPY.replace("a", "a​")))).isPresent();
        assertThat(guard.copiedFrom(Map.of("body", COPY.replace("board", "ｂｏａｒｄ")))).isPresent();
    }

    @Test
    void accents_stay_part_of_their_word() {
        String spanish = "La junta acordó recortar el presupuesto de marketing un cuarenta por ciento el próximo"
                + " trimestre.";
        CopyGuard read = new CopyGuard("");
        read.read(spanish, "Junta");

        String decomposed = spanish.replace("ó", "ó");
        assertThat(read.copiedFrom(Map.of("body", decomposed))).contains("Junta");
    }

    @Test
    void the_handed_meeting_may_be_quoted_and_short_or_own_words_go() {
        guard.read(HANDED, "Weekly sync");

        assertThat(guard.copiedFrom(Map.of("title", "Safari checkout", "body", HANDED))).isEmpty();
        assertThat(guard.copiedFrom(Map.of("body", "The board agreed to cut the marketing budget"))).isEmpty();
        assertThat(guard.copiedFrom(Map.of("count", 3, "ids", List.of(1, 2)))).isEmpty();
    }

    @Test
    void a_text_shorter_than_one_passage_gives_no_passages() {
        assertThat(CopyGuard.shingles("one two three")).isEmpty();
        assertThat(CopyGuard.shingles(null)).isEmpty();
        assertThat(CopyGuard.shingles("a b c d e f g h i j k l m 1 2 3")).hasSize(2);
        assertThat(CopyGuard.shingles("Aa b c d e f g h i j k l")).isNotEqualTo(CopyGuard.shingles("BB b c d e f g h i j k l"));
    }
}
