package com.conxist.capture.domain;

import java.util.Optional;

/** A display title taken from a file name or recording title, which are untrusted text. */
public record DocumentTitle(String value) {

    public static final int MAX_LENGTH = 200;
    static final String UNTITLED = "Untitled document";
    static final String UNTITLED_MEETING = "Untitled meeting";

    /** From a file name: keeps only the last path segment. */
    public static DocumentTitle from(String rawName) {
        return clean(Optional.ofNullable(rawName).map(DocumentTitle::lastPathSegment).orElse(""), UNTITLED);
    }

    /** From a recording's title, where a slash is just text ("Q3/Q4 planning"). */
    public static DocumentTitle ofMeeting(String rawTitle) {
        return clean(Optional.ofNullable(rawTitle).orElse(""), UNTITLED_MEETING);
    }

    private static DocumentTitle clean(String raw, String fallback) {
        String cleaned = raw.replaceAll("\\p{Cntrl}", "").strip();
        if (cleaned.isEmpty()) {
            return new DocumentTitle(fallback);
        }
        return new DocumentTitle(cleaned.length() > MAX_LENGTH ? cleaned.substring(0, MAX_LENGTH) : cleaned);
    }

    private static String lastPathSegment(String name) {
        return name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
    }
}
