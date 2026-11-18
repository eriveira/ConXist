package com.conxist.capture.domain;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Writes one recorded meeting as a Markdown document: title, when it was recorded, the recorder's summaries if any,
 * then the transcript with a timestamp and speaker on every line. The content is customer data and is stored as is.
 */
public final class MeetingText {

    public static final String MEDIA_TYPE = "text/markdown";
    private static final DateTimeFormatter RECORDED = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'")
            .withZone(ZoneOffset.UTC);

    private MeetingText() {
    }

    public record Line(String speaker, long startMs, String text) {
    }

    public static String compose(String title, Instant recordedAt, String language, List<String> summaries,
            List<Line> lines) {
        StringBuilder text = new StringBuilder("# ").append(title).append("\n\n");
        text.append("Recorded ").append(RECORDED.format(recordedAt));
        if (language != null && !language.isBlank()) {
            text.append(" · Language: ").append(language);
        }
        text.append("\n");
        for (String summary : summaries) {
            if (!summary.isBlank()) {
                text.append("\n## Summary\n\n").append(summary.strip()).append("\n");
            }
        }
        text.append("\n## Transcript\n\n");
        for (Line line : lines) {
            text.append('[').append(clock(line.startMs())).append("] ");
            if (line.speaker() != null && !line.speaker().isBlank()) {
                text.append(line.speaker().strip()).append(": ");
            }
            text.append(line.text().strip()).append("\n");
        }
        return text.toString();
    }

    static String clock(long startMs) {
        long seconds = Math.max(0, startMs) / 1000;
        return "%02d:%02d:%02d".formatted(seconds / 3600, (seconds % 3600) / 60, seconds % 60);
    }
}
