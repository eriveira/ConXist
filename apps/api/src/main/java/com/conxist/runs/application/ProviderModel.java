package com.conxist.runs.application;

/**
 * A model as its provider lists it (ADR 0014): its id and the name the provider gives it. The name comes from outside,
 * so it is shown as text only and cut to {@link #MAX_LABEL} characters.
 */
public record ProviderModel(String id, String label) {

    public static final int MAX_LABEL = 200;
    /** What a model id may hold: the database keeps up to 100 characters, and the app uses it in element ids. */
    private static final java.util.regex.Pattern ID = java.util.regex.Pattern.compile("[A-Za-z0-9._:-]{1,100}");

    public ProviderModel {
        if (!validId(id)) {
            throw new IllegalArgumentException("A model id must be 1 to 100 letters, digits, dots, colons or dashes.");
        }
        label = label == null ? "" : label.replaceAll("\\p{Cntrl}", " ").strip();
        label = label.isEmpty() ? id : label;
        if (label.length() > MAX_LABEL) {
            label = label.substring(0, MAX_LABEL);
        }
    }

    /** Whether a provider's model id can be kept and shown; others are left out of the list. */
    public static boolean validId(String id) {
        return id != null && ID.matcher(id).matches();
    }
}
