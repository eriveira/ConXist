package com.conxist.capture.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    /** The longest speaker name kept; longer ones are cut. */
    public static final int MAX_SPEAKER_LENGTH = 80;
    private static final int MAX_LANGUAGE_LENGTH = 16;
    private static final Pattern CONTROL = Pattern.compile("\\p{Cntrl}");
    private static final Pattern SPEAKER_UNSAFE = Pattern.compile("[^\\p{L}\\p{N} ._'-]");
    private static final Pattern LOOKS_SPOKEN = Pattern.compile("[\\p{L}\\p{N} ._'-]{1,80}: ");
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
            String speaker = speaker(line.speaker());
            text.append(said(speaker, oneLine(line.text()))).append("\n");
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

    /**
     * A person's corrections to a meeting {@link #compose} wrote: a new title, new names for speakers (each applies to
     * every line that speaker said), and new text for lines, by their position in the transcript. Times, the order of
     * lines, which speaker said each line and the summaries stay as recorded. The corrections are untrusted like the
     * rest: they are cleaned the same way {@link #compose} cleans what Plaud sends.
     */
    public static String edit(String document, String title, Map<String, String> speakers, Map<Integer, String> lines,
            int maxCharacters) {
        checkRenames(document, speakers);
        StringBuilder text = new StringBuilder();
        boolean titled = false;
        boolean inTranscript = false;
        int index = 0;
        String[] rows = document.split("\n", -1);
        for (int at = 0; at < rows.length; at++) {
            String row = rows[at];
            Matcher line = inTranscript ? TRANSCRIPT_LINE.matcher(row) : null;
            if (!titled && row.startsWith("# ")) {
                row = "# " + oneLine(title);
                titled = true;
            } else if (row.equals("## Transcript")) {
                inTranscript = true;
            } else if (line != null && line.matches()) {
                row = rewriteLine(line, speakers, lines.get(index));
                index++;
            }
            text.append(row);
            if (at < rows.length - 1) {
                text.append('\n');
            }
        }
        checkLimits(lines, index, text, maxCharacters);
        return text.toString();
    }

    /** Each rename names a speaker of this meeting and gives a name that is not empty once cleaned. */
    private static void checkRenames(String document, Map<String, String> speakers) {
        Set<String> known = new HashSet<>();
        read(document).lines().forEach(line -> known.add(line.speaker()));
        for (Map.Entry<String, String> rename : speakers.entrySet()) {
            if (rename.getKey().isEmpty() || !known.contains(rename.getKey())) {
                throw new RejectedDocument("meeting.unknown-speaker", "That speaker is not in this meeting.");
            }
            if (speaker(rename.getValue()).isEmpty()) {
                throw new RejectedDocument("meeting.speaker-empty", "Give each speaker a name.");
            }
        }
    }

    /** One transcript line with its speaker renamed and, when given, its new words; the time stays. */
    private static String rewriteLine(Matcher line, Map<String, String> speakers, String newWords) {
        String said = line.group(2) == null ? "" : line.group(2);
        String speaker = speaker(speakers.getOrDefault(said, said));
        String words = newWords != null ? oneLine(newWords) : line.group(3);
        return "[" + line.group(1) + "] " + said(speaker, words);
    }

    /**
     * A line's speaker and words as written. Words on a line with no speaker that start like "Name: " would read back
     * as said by that name, so their first ": " becomes " - ": nobody can be made to say something by typing it.
     */
    private static String said(String speaker, String words) {
        if (!speaker.isEmpty()) {
            return speaker + ": " + words;
        }
        return LOOKS_SPOKEN.matcher(words).lookingAt() ? words.replaceFirst(": ", " - ") : words;
    }

    private static void checkLimits(Map<Integer, String> lines, int lineCount, CharSequence text, int maxCharacters) {
        if (lines.keySet().stream().anyMatch(position -> position < 0 || position >= lineCount)) {
            throw new RejectedDocument("meeting.unknown-line", "That line is not in this meeting.");
        }
        if (text.length() > maxCharacters) {
            throw new RejectedDocument("meeting.too-long", "This meeting would be longer than ConXist keeps.");
        }
    }

    /** A speaker's name as {@link #compose} keeps it: letters, digits, spaces and a few marks, at most 80 characters. */
    private static String speaker(String name) {
        String cleaned = name == null ? "" : SPEAKER_UNSAFE.matcher(name).replaceAll("").strip();
        return cleaned.substring(0, Math.min(cleaned.length(), MAX_SPEAKER_LENGTH));
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
