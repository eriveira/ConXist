package com.conxist.capture.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import com.conxist.capture.domain.MeetingText.Line;
import org.junit.jupiter.api.Test;

class MeetingTextTest {

    @Test
    void writes_title_date_summary_and_a_timestamped_line_per_speaker_turn() {
        String text = MeetingText.compose("Sprint planning", Instant.parse("2026-11-16T15:04:00Z"), "en",
                List.of("We agreed on the scope.", " "),
                List.of(new Line("Ana", 0, "Let's start."), new Line("", 3_725_000, " Next item. ")));

        assertThat(text).isEqualTo("""
                # Sprint planning

                Recorded 2026-11-16 15:04 UTC · Language: en

                ## Summary

                We agreed on the scope.

                ## Transcript

                [00:00:00] Ana: Let's start.
                [01:02:05] Next item.
                """);
    }
}
