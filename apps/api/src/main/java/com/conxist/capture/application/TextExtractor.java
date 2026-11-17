package com.conxist.capture.application;

/** Reads what a file is and the text inside it. */
public interface TextExtractor {

    String detectMediaType(byte[] content, String fileName);

    /** Returns the file's text, or throws a RejectedDocument when it cannot be read. */
    String extractText(byte[] content, String mediaType);
}
