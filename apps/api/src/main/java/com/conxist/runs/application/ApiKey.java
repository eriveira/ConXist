package com.conxist.runs.application;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A key that calls an LLM provider. It is passed only to the provider's adapter and the encrypted store: it never goes
 * into a log, an error or an answer, and {@link #toString()} hides it.
 */
public record ApiKey(String value) {

    /** Printable characters without spaces, as provider keys are; a pasted key is trimmed first. */
    private static final Pattern SHAPE = Pattern.compile("[\\x21-\\x7E]{8,500}");
    private static final int ENDING = 4;

    public ApiKey {
        Objects.requireNonNull(value, "value");
    }

    /** The key a person pasted, trimmed, or empty when it can't be a provider key. */
    public static Optional<ApiKey> pasted(String text) {
        String trimmed = text == null ? "" : text.strip();
        return SHAPE.matcher(trimmed).matches() ? Optional.of(new ApiKey(trimmed)) : Optional.empty();
    }

    /** The last few characters, the only part of a key ConXist shows again. */
    public String ending() {
        return value.substring(Math.max(0, value.length() - ENDING));
    }

    @Override
    public String toString() {
        return "ApiKey[hidden]";
    }
}
