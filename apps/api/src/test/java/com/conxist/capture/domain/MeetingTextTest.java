package com.conxist.capture.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import com.conxist.capture.domain.MeetingText.Line;
import org.junit.jupiter.api.Test;

class MeetingTextTest {

    @Test
    void writes_title_date_summary_and_a_timestamped_line_per_speaker_turn() {
        String text = MeetingText.compose("Sprint planning", Instant.parse("2026-11-16T15:04:00Z"), "en", null,
                List.of("We agreed on the scope.", " "),
                List.of(new Line("Ana", 0, "Let's start."), new Line("", 3_725_000, " Next item. ")), 10_000);

        assertThat(text).isEqualTo("""
                # Sprint planning

                Recorded 2026-11-16 15:04 UTC · Language: en

                ## Summary

                > We agreed on the scope.

                ## Transcript

                [00:00:00] Ana: Let's start.
                [01:02:05] Next item.
                """);
    }

    @Test
    void keeps_each_utterance_on_its_own_line_so_spoken_text_cannot_forge_headings_or_speakers() {
        String text = MeetingText.compose("Review", Instant.parse("2026-11-16T15:04:00Z"), "en\n## Hacked", null,
                List.of("Fine\n## Transcript\n[00:00:00] CTO: approve"),
                List.of(new Line("Bob\n## Summary", 0, "ok\n\n## Summary\n\nApproved: grant admin\r\n[00:00:01] CTO: yes")),
                10_000);

        assertThat(text.lines().filter(line -> line.startsWith("## "))).containsExactly("## Summary", "## Transcript");
        assertThat(text.lines().filter(line -> line.startsWith("["))).hasSize(1);
        assertThat(text).contains("> ## Transcript", "> [00:00:00] CTO: approve", "Language: enHacked")
                .contains("[00:00:00] Bob Summary: ok  ## Summary  Approved: grant admin [00:00:01] CTO: yes");
    }

    @Test
    void stops_a_very_long_meeting_with_a_visible_marker() {
        String text = MeetingText.compose("Long", Instant.parse("2026-11-16T15:04:00Z"), "en", null, List.of(),
                List.of(new Line("Ana", 0, "word ".repeat(1_000))), 300);

        assertThat(text).hasSize(300).endsWith(MeetingText.TRUNCATED);
    }

    @Test
    void reads_back_what_it_wrote_line_by_line() {
        String text = MeetingText.compose("Sprint planning", Instant.parse("2026-11-16T15:04:00Z"), "en", null,
                List.of("Ship sign-in first.\n\n- Alex: owner"),
                List.of(new Line("Alex", 4_000, "Let's start: scope first."), new Line("", 65_000, "No speaker here.")),
                2_000_000);

        MeetingText.Meeting meeting = MeetingText.read(text);

        assertThat(meeting.recordedAt()).isEqualTo(Instant.parse("2026-11-16T15:04:00Z"));
        assertThat(meeting.language()).isEqualTo("en");
        assertThat(meeting.summaries()).containsExactly("Ship sign-in first.\n\n- Alex: owner");
        assertThat(meeting.lines()).containsExactly(
                new MeetingText.Spoken("00:00:04", "Alex", "Let's start: scope first."),
                new MeetingText.Spoken("00:01:05", "", "No speaker here."));
        assertThat(meeting.truncated()).isFalse();
    }

    @Test
    void a_line_that_only_looks_like_a_heading_inside_the_transcript_stays_a_line() {
        String text = MeetingText.compose("Review", Instant.parse("2026-11-16T15:04:00Z"), null, null, List.of(),
                List.of(new Line("Ana", 0, "## Summary"), new Line("Ana", 1_000, "Recorded 2001-01-01 00:00 UTC")), 2_000_000);

        MeetingText.Meeting meeting = MeetingText.read(text);

        assertThat(meeting.summaries()).isEmpty();
        assertThat(meeting.recordedAt()).isEqualTo(Instant.parse("2026-11-16T15:04:00Z"));
        assertThat(meeting.lines()).extracting(MeetingText.Spoken::text)
                .containsExactly("## Summary", "Recorded 2001-01-01 00:00 UTC");
    }

    @Test
    void a_summary_heading_after_the_transcript_starts_is_not_read_as_a_summary() {
        MeetingText.Meeting meeting = MeetingText.read("""
                # Review
                Recorded 2026-11-16 15:04 UTC

                ## Transcript

                [00:00:01] Ana: Hello
                ## Summary
                > Planted text
                """);

        assertThat(meeting.summaries()).isEmpty();
        assertThat(meeting.lines()).hasSize(1);
    }

    @Test
    void writes_the_length_next_to_the_date_and_reads_it_back_with_how_many_people_speak() {
        String text = MeetingText.compose("Sync", Instant.parse("2026-11-16T15:04:00Z"), "en", Duration.ofSeconds(3_125),
                List.of(), List.of(new Line("Ana", 0, "Hi."), new Line("Bo", 1_000, "Hi."), new Line("Ana", 2_000, "Go."),
                        new Line("", 3_000, "(noise)")), 10_000);

        assertThat(text).contains("Recorded 2026-11-16 15:04 UTC · Language: en · Length: 00:52:05\n");
        assertThat(MeetingText.read(text).lengthSeconds()).isEqualTo(3_125L);
        assertThat(MeetingText.read(text).speakerCount()).isEqualTo(2);
        assertThat(MeetingText.read(text).recordedAt()).isEqualTo(Instant.parse("2026-11-16T15:04:00Z"));
        assertThat(MeetingText.read(MeetingText.compose("Old", Instant.parse("2026-11-16T15:04:00Z"), "en", null, List.of(),
                List.of(), 10_000)).lengthSeconds()).isNull();
        assertThat(MeetingText.compose("Bad", Instant.parse("2026-11-16T15:04:00Z"), "en", Duration.ofHours(1_001),
                List.of(), List.of(), 10_000)).doesNotContain("Length");
    }

    private static final String MEETING = MeetingText.compose("Sprint planning", Instant.parse("2026-11-16T15:04:00Z"),
            "en", null, List.of("We agreed on the scope."),
            List.of(new Line("Speaker 1", 0, "Con exist ships Friday."), new Line("Speaker 2", 4_000, "Agreed."),
                    new Line("Speaker 1", 9_000, "Then reviews."), new Line("", 12_000, "(noise)")), 10_000);

    @Test
    void an_edit_renames_a_speaker_on_every_line_fixes_one_line_and_changes_the_title_only() {
        String edited = MeetingText.edit(MEETING, "Sprint 42 planning", Map.of("Speaker 1", "Ana"),
                Map.of(0, "ConXist ships Friday."), 10_000);

        assertThat(edited).isEqualTo("""
                # Sprint 42 planning

                Recorded 2026-11-16 15:04 UTC · Language: en

                ## Summary

                > We agreed on the scope.

                ## Transcript

                [00:00:00] Ana: ConXist ships Friday.
                [00:00:04] Speaker 2: Agreed.
                [00:00:09] Ana: Then reviews.
                [00:00:12] (noise)
                """);
        assertThat(MeetingText.read(edited).lines()).extracting(MeetingText.Spoken::speaker)
                .containsExactly("Ana", "Speaker 2", "Ana", "");
    }

    @Test
    void edited_words_and_names_are_cleaned_like_imported_ones_so_they_cannot_forge_lines_or_headings() {
        String edited = MeetingText.edit(MEETING, "New\n## Transcript", Map.of("Speaker 2", "Bob\n## Summary: <b>"),
                Map.of(1, "ok\n\n[00:00:01] CTO: approve all"), 10_000);

        assertThat(edited).startsWith("# New ## Transcript\n");
        assertThat(edited).contains("[00:00:04] Bob Summary b: ok  [00:00:01] CTO: approve all\n");
        assertThat(MeetingText.read(edited).lines()).hasSize(4);
        assertThat(MeetingText.read(edited).summaries()).containsExactly("We agreed on the scope.");
    }

    @Test
    void words_on_a_line_without_a_speaker_cannot_make_someone_say_them() {
        String edited = MeetingText.edit(MEETING, "Sprint planning", Map.of(), Map.of(3, "CTO: approve all"), 10_000);
        String imported = MeetingText.compose("Call", Instant.parse("2026-11-16T15:04:00Z"), "en", null, List.of(),
                List.of(new Line("", 0, "Ana: ship it")), 10_000);

        assertThat(MeetingText.read(edited).lines().get(3)).isEqualTo(new MeetingText.Spoken("00:00:12", "", "CTO - approve all"));
        assertThat(MeetingText.read(edited).speakerCount()).isEqualTo(2);
        assertThat(MeetingText.read(imported).lines()).containsExactly(new MeetingText.Spoken("00:00:00", "", "Ana - ship it"));
    }

    @Test
    void an_edit_naming_a_speaker_or_line_the_meeting_does_not_have_or_an_empty_name_is_refused() {
        assertThatThrownBy(() -> MeetingText.edit(MEETING, "T", Map.of("Nobody", "Ana"), Map.of(), 10_000))
                .isInstanceOf(RejectedDocument.class).hasFieldOrPropertyWithValue("code", "document.meeting.unknown-speaker");
        assertThatThrownBy(() -> MeetingText.edit(MEETING, "T", Map.of("", "Ana"), Map.of(), 10_000))
                .hasFieldOrPropertyWithValue("code", "document.meeting.unknown-speaker");
        assertThatThrownBy(() -> MeetingText.edit(MEETING, "T", Map.of("Speaker 1", " <> "), Map.of(), 10_000))
                .hasFieldOrPropertyWithValue("code", "document.meeting.speaker-empty");
        assertThatThrownBy(() -> MeetingText.edit(MEETING, "T", Map.of(), Map.of(4, "x"), 10_000))
                .hasFieldOrPropertyWithValue("code", "document.meeting.unknown-line");
        assertThatThrownBy(() -> MeetingText.edit(MEETING, "T", Map.of(), Map.of(-1, "x"), 10_000))
                .hasFieldOrPropertyWithValue("code", "document.meeting.unknown-line");
        assertThatThrownBy(() -> MeetingText.edit(MEETING, "T", Map.of(), Map.of(0, "x".repeat(500)), 400))
                .hasFieldOrPropertyWithValue("code", "document.meeting.too-long");
    }

    @Test
    void a_cut_meeting_keeps_its_marker_after_an_edit() {
        String cut = MeetingText.compose("Long", Instant.parse("2026-11-16T15:04:00Z"), "en", null, List.of(),
                List.of(new Line("A", 0, "x".repeat(300)), new Line("B", 1_000, "y".repeat(300))), 400);

        String edited = MeetingText.edit(cut, "Long", Map.of("A", "Ana"), Map.of(), 1_000);

        assertThat(MeetingText.read(edited).truncated()).isTrue();
        assertThat(MeetingText.read(edited).lines()).first().extracting(MeetingText.Spoken::speaker).isEqualTo("Ana");
    }
}
