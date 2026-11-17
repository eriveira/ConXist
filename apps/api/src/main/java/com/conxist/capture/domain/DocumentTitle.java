package com.conxist.capture.domain;

import java.util.Optional;

/** A display title taken from a file name or recording title, which are untrusted text. */
public record DocumentTitle(String value) {

    public static final int MAX_LENGTH = 200;
    static final String UNTITLED = "Untitled document";

    public static DocumentTitle from(String rawName) {
        String cleaned = Optional.ofNullable(rawName)
                .map(DocumentTitle::lastPathSegment)
                .map(name -> name.replaceAll("\\p{Cntrl}", ""))
                .map(String::strip)
                .orElse("");
        if (cleaned.isEmpty()) {
            return new DocumentTitle(UNTITLED);
        }
        return new DocumentTitle(cleaned.length() > MAX_LENGTH ? cleaned.substring(0, MAX_LENGTH) : cleaned);
    }

    private static String lastPathSegment(String name) {
        return name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
    }
}
