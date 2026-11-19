package com.conxist.capture.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
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
    /** Longer than any real recording: a length past it is a bad answer and is left out, not written. */
    private static final Duration MAX_LENGTH = Duration.ofHours(1_000);
    private static final Pattern RECORDED_LINE = Pattern.compile(
            "^Recorded (\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}) UTC(?: · Language: ([A-Za-z-]{1,16}))?(?: · Length: (\\d{2,6}):(\\d{2}):(\\d{2}))?$");
    private static final Pattern TRANSCRIPT_LINE = Pattern.compile(
            "^\\[(\\d{2}:\\d{2}:\\d{2})\\] (?:([\\p{L}\\p{N} ._'-]{1,80}): )?(.*)$");

    private MeetingText() {
    }

    public record Line(String speaker, long startMs, String text) {
    }

    /**
     * Transcripts and summaries are untrusted: a speaker could say, or a Plaud member could type, text that looks like
     * a heading or another speaker's line. Each utterance and speaker is kept on one line, summaries are quoted, and
     * the whole text stops at {@code maxCharacters} with a visible marker. {@code length} is how long the recording
     * runs, when the recorder says (null otherwise); it is written next to the date.
     */
    public static String compose(String title, Instant recordedAt, String language, Duration length,
            List<String> summaries, List<Line> lines, int maxCharacters) {
        StringBuilder text = new StringBuilder("# ").append(oneLine(title)).append("\n\n");
        text.append("Recorded ").append(RECORDED.format(recordedAt));
        String spoken = language == null ? "" : LANGUAGE_UNSAFE.matcher(language).replaceAll("");
        if (!spoken.isBlank()) {
            text.append(" · Language: ").append(spoken, 0, Math.min(spoken.length(), MAX_LANGUAGE_LENGTH));
        }
        if (length != null && !length.isNegative() && !length.isZero() && length.compareTo(MAX_LENGTH) <= 0) {
            text.append(" · Length: ").append(clock(length.toMillis()));
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

    /** A meeting read back from its document, to show it: the parts {@link #compose} wrote, in order. */
    public record Meeting(Instant recordedAt, String language, List<String> summaries, List<Spoken> lines,
            boolean truncated, Long lengthSeconds) {

        /** The people who speak, each once. */
        public int speakerCount() {
            return (int) lines.stream().map(Spoken::speaker).filter(speaker -> !speaker.isEmpty()).distinct().count();
        }
    }

    /** One transcript line as written: {@code at} is "hh:mm:ss" from the start; {@code speaker} may be empty. */
    public record Spoken(String at, String speaker, String text) {
    }

    /** Reads a document {@link #compose} wrote. Anything it does not recognise is left out rather than guessed. */
    public static Meeting read(String document) {
        Instant recordedAt = null;
        String language = null;
        Long lengthSeconds = null;
        List<String> summaries = new ArrayList<>();
        List<Spoken> lines = new ArrayList<>();
        StringBuilder summary = null;
        boolean inTranscript = false;
        for (String row : document.split("\n", -1)) {
            Matcher recorded = RECORDED_LINE.matcher(row);
            if (!inTranscript && recordedAt == null && recorded.matches()) {
                recordedAt = utc(recorded.group(1));
                language = recorded.group(2);
                lengthSeconds = seconds(recorded);
            } else if (!inTranscript && row.equals("## Summary")) {
                summary = new StringBuilder();
                summaries.add("");
            } else if (row.equals("## Transcript")) {
                inTranscript = true;
                summary = null;
            } else if (summary != null && row.startsWith("> ")) {
                summary.append(summary.isEmpty() ? "" : "\n").append(row.substring(2));
                summaries.set(summaries.size() - 1, summary.toString());
            } else if (summary != null && row.equals(">")) {
                summary.append("\n");
            } else if (inTranscript) {
                Matcher line = TRANSCRIPT_LINE.matcher(row);
                if (line.matches()) {
                    lines.add(new Spoken(line.group(1), line.group(2) == null ? "" : line.group(2), line.group(3)));
                }
            }
        }
        summaries.removeIf(String::isBlank);
        return new Meeting(recordedAt, language, List.copyOf(summaries), List.copyOf(lines),
                document.endsWith(TRUNCATED), lengthSeconds);
    }

    private static Long seconds(Matcher recorded) {
        if (recorded.group(3) == null) {
            return null;
        }
        return Long.parseLong(recorded.group(3)) * 3600 + Long.parseLong(recorded.group(4)) * 60
                + Long.parseLong(recorded.group(5));
    }

    private static Instant utc(String value) {
        try {
            return LocalDateTime.parse(value.replace(' ', 'T')).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException unreadable) {
            return null;
        }
    }

    /** Line breaks and control characters become spaces, so one utterance can never start a new line. */
    static String oneLine(String value) {
        return value == null ? "" : CONTROL.matcher(value.replaceAll("\\R", " ")).replaceAll(" ").strip();
    }

    /** hh:mm:ss; the hours keep counting past 99 rather than wrap. */
    static String clock(long startMs) {
        long seconds = Math.max(0, startMs) / 1000;
        return "%02d:%02d:%02d".formatted(seconds / 3600, (seconds % 3600) / 60, seconds % 60);
    }
}
