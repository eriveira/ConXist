package com.conxist.connectors.domain;

/** Trimming text a person or a service gave to what ConXist keeps. */
final class Texts {

    private Texts() {
    }

    /** Stripped, and cut at {@code max} code points with an ellipsis; empty for null. */
    static String cut(String value, int max) {
        String text = value == null ? "" : value.strip();
        return text.codePointCount(0, text.length()) <= max ? text
                : text.substring(0, text.offsetByCodePoints(0, max - 1)) + "…";
    }
}
