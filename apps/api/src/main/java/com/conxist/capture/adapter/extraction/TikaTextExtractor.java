package com.conxist.capture.adapter.extraction;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import com.conxist.capture.application.TextExtractor;
import com.conxist.capture.domain.RejectedDocument;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Reads PDF and Word files with Apache Tika; plain text and Markdown are read as UTF-8. */
public class TikaTextExtractor implements TextExtractor {

    static final String UNREADABLE = "We could not read the text in this file.";
    private static final Logger log = LoggerFactory.getLogger(TikaTextExtractor.class);

    private final Tika tika = new Tika();
    private final int maxCharacters;

    public TikaTextExtractor(int maxCharacters) {
        this.maxCharacters = maxCharacters;
    }

    @Override
    public String detectMediaType(byte[] content, String fileName) {
        return tika.detect(content, fileName);
    }

    @Override
    public String extractText(byte[] content, String mediaType) {
        String text = mediaType.startsWith("text/") ? decodeText(content) : parse(content);
        String trimmed = withoutControlCharacters(text).strip();
        if (trimmed.isEmpty()) {
            throw new RejectedDocument("no-text", "This file has no text we can read.");
        }
        return trimmed.length() > maxCharacters ? trimmed.substring(0, maxCharacters) : trimmed;
    }

    /** PostgreSQL cannot store NUL, and other control characters (except tab and line breaks) carry no text. */
    static String withoutControlCharacters(String text) {
        return text.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "");
    }

    private static String decodeText(byte[] content) {
        return new String(content, StandardCharsets.UTF_8);
    }

    private String parse(byte[] content) {
        try (InputStream stream = new ByteArrayInputStream(content)) {
            return tika.parseToString(stream, new Metadata(), maxCharacters);
        } catch (IOException | TikaException failure) {
            // The file content is customer data, so only the failure type is logged.
            log.warn("Text extraction failed: {}", failure.getClass().getSimpleName());
            throw new RejectedDocument("unreadable", UNREADABLE);
        }
    }
}
