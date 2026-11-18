package com.conxist.capture.domain;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Writes one recorded meeting as a Markdown document: title, when it was recorded, the recorder's summaries if any,
 * then the transcript with a timestamp and speaker on every line. The content is customer data and untrusted input.
 */
public final class MeetingText {

    public static final String MEDIA_TYPE = "text/markdown";
    private static final DateTimeFormatter RECORDED = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'")
            .withZone(ZoneOffset.UTC);

    static final String TRUNCATED = "\n\n[truncated: this meeting is longer than ConXist keeps]\n";
    private static final int MAX_SPEAKER_LENGTH = 80;
    private static final int MAX_LANGUAGE_LENGTH = 16;
    private static final Pattern CONTROL = Pattern.compile("\\p{Cntrl}");
    private static final Pattern SPEAKER_UNSAFE = Pattern.compile("[^\\p{L}\\p{N} ._'-]");
    private static final Pattern LANGUAGE_UNSAFE = Pattern.compile("[^A-Za-z-]");

    private MeetingText() {
    }

    public record Line(String speaker, long startMs, String text) {
    }

    /**
     * Transcripts and summaries are untrusted: a speaker could say, or a Plaud member could type, text that looks like
     * a heading or another speaker's line. Each utterance and speaker is kept on one line, summaries are quoted, and
     * the whole text stops at {@code maxCharacters} with a visible marker.
     */
    public static String compose(String title, Instant recordedAt, String language, List<String> summaries,
            List<Line> lines, int maxCharacters) {
        StringBuilder text = new StringBuilder("# ").append(oneLine(title)).append("\n\n");
        text.append("Recorded ").append(RECORDED.format(recordedAt));
        String spoken = language == null ? "" : LANGUAGE_UNSAFE.matcher(language).replaceAll("");
        if (!spoken.isBlank()) {
            text.append(" · Language: ").append(spoken, 0, Math.min(spoken.length(), MAX_LANGUAGE_LENGTH));
        }
        text.append("\n");
        for (String summary : summaries) {
            if (!summary.isBlank()) {
                text.append("\n## Summary\n\n");
                summary.strip().lines().forEach(row -> text.append("> ").append(CONTROL.matcher(row).replaceAll(""))
                        .append("\n"));
            }
        }
        text.append("\n## Transcript\n\n");
        for (Line line : lines) {
            text.append('[').append(clock(line.startMs())).append("] ");
            String speaker = line.speaker() == null ? "" : SPEAKER_UNSAFE.matcher(line.speaker()).replaceAll("").strip();
            if (!speaker.isEmpty()) {
                text.append(speaker, 0, Math.min(speaker.length(), MAX_SPEAKER_LENGTH)).append(": ");
            }
            text.append(oneLine(line.text())).append("\n");
        }
        if (text.length() > maxCharacters) {
            text.setLength(Math.max(0, maxCharacters - TRUNCATED.length()));
            text.append(TRUNCATED);
        }
        return text.toString();
    }

    /** Line breaks and control characters become spaces, so one utterance can never start a new line. */
    static String oneLine(String value) {
        return value == null ? "" : CONTROL.matcher(value.replaceAll("\\R", " ")).replaceAll(" ").strip();
    }

    static String clock(long startMs) {
        long seconds = Math.max(0, startMs) / 1000;
        return "%02d:%02d:%02d".formatted(seconds / 3600, (seconds % 3600) / 60, seconds % 60);
    }
}
