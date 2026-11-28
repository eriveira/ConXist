package com.conxist.runs.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keeps a run from copying what it read into a tool that reaches outside ConXist (ADR 0025, layer 3). A hostile
 * source can ask the model to send it, or another meeting, somewhere; the model may obey, so code refuses the call:
 * <ul>
 * <li>any outside tool whose input repeats {@value #SHINGLE_WORDS} words in a row of something the run read, other
 * than the meeting or text it was handed, which its instructions may quote;</li>
 * <li>a tool that changes things whose input holds more than {@value #MAX_CHANGE_CHARACTERS} characters of text.</li>
 * </ul>
 * It remembers word runs only as numbers, never the text.
 */
public final class CopyGuard {

    /** How many words in a row count as a copied passage. */
    public static final int SHINGLE_WORDS = 12;
    /** The most text one change may send, in characters: a few paragraphs written by the agent. */
    public static final int MAX_CHANGE_CHARACTERS = 4_000;

    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+");

    private final Set<Long> handed;
    /** Each passage read, to the name of where it was read. */
    private final Map<Long, String> read = new HashMap<>();

    /** @param handedText the meeting or text the run was handed */
    public CopyGuard(String handedText) {
        this.handed = new HashSet<>(shingles(handedText));
    }

    /** Remembers what a tool answered, and where: a meeting's title or a tool's name, shown only to the person. */
    public void read(String text, String from) {
        for (long shingle : shingles(text)) {
            if (!handed.contains(shingle)) {
                read.putIfAbsent(shingle, from);
            }
        }
    }

    /** Why ConXist does not send this input, or empty when it may go. */
    public Optional<Refusal> check(Map<String, Object> input, boolean changesThings) {
        List<String> texts = new ArrayList<>();
        collect(input, texts);
        if (changesThings && texts.stream().mapToLong(String::length).sum() > MAX_CHANGE_CHARACTERS) {
            return Optional.of(new Refusal(Refusal.Reason.TOO_LONG, null));
        }
        for (String text : texts) {
            for (long shingle : shingles(text)) {
                String from = read.get(shingle);
                if (from != null) {
                    return Optional.of(new Refusal(Refusal.Reason.COPIES_SOURCE, from));
                }
            }
        }
        return Optional.empty();
    }

    private static void collect(Object value, List<String> texts) {
        if (value instanceof String text) {
            texts.add(text);
        } else if (value instanceof Map<?, ?> map) {
            map.values().forEach(item -> collect(item, texts));
        } else if (value instanceof Iterable<?> items) {
            items.forEach(item -> collect(item, texts));
        }
    }

    /** Each run of {@value #SHINGLE_WORDS} words, lower-cased with punctuation dropped, as a 64-bit hash. */
    static List<Long> shingles(String text) {
        List<Integer> words = new ArrayList<>();
        Matcher matcher = WORD.matcher(text == null ? "" : text);
        while (matcher.find()) {
            words.add(matcher.group().toLowerCase(Locale.ROOT).hashCode());
        }
        List<Long> shingles = new ArrayList<>();
        for (int start = 0; start + SHINGLE_WORDS <= words.size(); start++) {
            long hash = 0xcbf29ce484222325L;
            for (int i = start; i < start + SHINGLE_WORDS; i++) {
                hash = (hash ^ words.get(i)) * 0x100000001b3L;
            }
            shingles.add(hash);
        }
        return shingles;
    }

    /** @param from where the copied passage was read, for {@link Reason#COPIES_SOURCE} */
    public record Refusal(Reason reason, String from) {

        public enum Reason {
            /** The input repeats a passage the run read. */
            COPIES_SOURCE,
            /** A change holds more text than {@link #MAX_CHANGE_CHARACTERS}. */
            TOO_LONG
        }
    }
}
