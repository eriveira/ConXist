package com.conxist.connectors.domain;

import java.net.URI;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.conxist.platform.RuleViolation;

/**
 * A logo a person chose (ADR 0022, ADR 0027): fetched once from an image address, or uploaded as a file. ConXist keeps
 * its own copy, so it still shows if that address changes.
 *
 * @param image     the copy, as a {@code data:image/...} address
 * @param address   where it was fetched from, to show the person; null when it was uploaded
 * @param fetchedAt when
 */
public record Logo(String image, URI address, Instant fetchedAt) {

    public static final int MAX_BYTES = 100 * 1024;
    private static final Pattern DATA = Pattern.compile(
            "data:(image/(?:png|jpeg|svg\\+xml|webp|x-icon|vnd\\.microsoft\\.icon));base64,([A-Za-z0-9+/=\\s]+)");

    public Logo {
        Objects.requireNonNull(image, "image");
        Objects.requireNonNull(fetchedAt, "fetchedAt");
    }

    /**
     * A file a person uploaded, as the browser reads it ({@code data:image/png;base64,...}). Only a PNG, JPG, SVG, WebP
     * or ICO up to 100 KB whose first bytes match its type.
     */
    public static Logo uploaded(String dataUrl, Instant now) {
        String value = dataUrl == null ? "" : dataUrl.strip();
        if (value.length() > MAX_BYTES * 4 / 3 + 200) {
            throw tooBig();
        }
        Matcher match = DATA.matcher(value);
        if (!match.matches()) {
            throw notImage();
        }
        byte[] bytes;
        try {
            bytes = Base64.getMimeDecoder().decode(match.group(2));
        } catch (IllegalArgumentException unreadable) {
            throw notImage();
        }
        if (bytes.length > MAX_BYTES) {
            throw tooBig();
        }
        if (!looksLike(match.group(1), bytes)) {
            throw notImage();
        }
        return new Logo("data:" + match.group(1) + ";base64," + Base64.getEncoder().encodeToString(bytes), null, now);
    }

    private static boolean looksLike(String type, byte[] bytes) {
        return switch (type) {
            case "image/png" -> starts(bytes, 0x89, 'P', 'N', 'G');
            case "image/jpeg" -> starts(bytes, 0xFF, 0xD8);
            case "image/webp" -> bytes.length > 12 && starts(bytes, 'R', 'I', 'F', 'F')
                    && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
            case "image/svg+xml" -> new String(bytes, 0, Math.min(bytes.length, 2_000),
                    java.nio.charset.StandardCharsets.UTF_8).contains("<svg");
            default -> starts(bytes, 0, 0, 1, 0);
        };
    }

    private static boolean starts(byte[] bytes, int... prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if ((bytes[i] & 0xFF) != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static RuleViolation notImage() {
        return new RuleViolation("logo.not-image", "That file isn't an image. Use a PNG, JPG, SVG, WebP or ICO.");
    }

    private static RuleViolation tooBig() {
        return new RuleViolation("logo.too-big", "That image is over 100 KB. Use a smaller one.");
    }
}
