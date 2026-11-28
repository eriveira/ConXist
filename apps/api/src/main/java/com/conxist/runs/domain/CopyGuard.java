package com.conxist.runs.domain;

import java.text.Normalizer;
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
 * source can ask the model to send it, or another meeting, somewhere; the model may obey, so code refuses any outside
 * call whose input repeats {@value #SHINGLE_WORDS} words in a row of something the run read, other than the meeting
 * or text it was handed, which its instructions may quote.
 * <p>
 * All of an input's text counts as one, keys included, so a passage split over fields or list items is still found.
 * Text is folded first (compatibility forms, invisible characters, accents written as separate marks, case), and
 * numbers do not count as words, so numbering each word does not break a passage. It is a floor, not a fence: a
 * paraphrase, a translation, an encoding, a filler word every few words, or text spread over several calls goes
 * through; later layers of ADR 0025 cover those. Words are runs of letters, which suits English and Spanish but not
 * scripts written without spaces. It remembers passages only as numbers, never the text.
 */
public final class CopyGuard {

    /** How many words in a row count as a copied passage. */
    public static final int SHINGLE_WORDS = 12;

    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{M}\\p{N}]+");
    private static final Pattern INVISIBLE = Pattern.compile("\\p{Cf}");
    private static final Pattern NUMBER = Pattern.compile("\\p{N}+");

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

    /** Where the input's copied passage was read, or empty when the input may go. */
    public Optional<String> copiedFrom(Map<String, Object> input) {
        List<String> texts = new ArrayList<>();
        collect(input, texts);
        for (long shingle : shingles(String.join(" ", texts))) {
            String from = read.get(shingle);
            if (from != null) {
                return Optional.of(from);
            }
        }
        return Optional.empty();
    }

    private static void collect(Object value, List<String> texts) {
        if (value instanceof String text) {
            texts.add(text);
        } else if (value instanceof Map<?, ?> map) {
            map.forEach((key, item) -> {
                collect(String.valueOf(key), texts);
                collect(item, texts);
            });
        } else if (value instanceof Iterable<?> items) {
            items.forEach(item -> collect(item, texts));
        }
    }

    /** Each run of {@value #SHINGLE_WORDS} words of the folded text, as a 64-bit hash. */
    static List<Long> shingles(String text) {
        List<Long> words = new ArrayList<>();
        Matcher matcher = WORD.matcher(folded(text));
        while (matcher.find()) {
            if (!NUMBER.matcher(matcher.group()).matches()) {
                words.add(fnv(matcher.group(), 0xcbf29ce484222325L));
            }
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

    /** Compatibility forms as plain letters, invisible characters gone, accents as part of their letter, lower case. */
    private static String folded(String text) {
        String plain = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFKC);
        return INVISIBLE.matcher(plain).replaceAll("").toLowerCase(Locale.ROOT);
    }

    /** FNV-1a over a word's characters, so words that share a 32-bit {@code hashCode} still differ. */
    private static long fnv(String word, long seed) {
        long hash = seed;
        for (int i = 0; i < word.length(); i++) {
            hash = (hash ^ word.charAt(i)) * 0x100000001b3L;
        }
        return hash;
    }
}
