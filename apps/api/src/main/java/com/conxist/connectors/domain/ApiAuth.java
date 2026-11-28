package com.conxist.connectors.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * How ConXist signs in to a custom API (ADR 0027). The secret itself (key, token or password) is kept apart, sealed;
 * this says only where it goes.
 *
 * @param kind  none, an API key, a bearer token, or a username and password (Basic)
 * @param place for an API key: a header or a query parameter; null otherwise
 * @param name  for an API key: the header's or the parameter's name; null otherwise
 */
public record ApiAuth(Kind kind, Place place, String name) {

    public enum Kind { NONE, API_KEY, BEARER, BASIC }

    public enum Place { HEADER, QUERY }

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_.-]{1,64}");
    /** Headers HTTP itself sets; a key there would break the request or reach another host. */
    static final Set<String> RESERVED_HEADERS = Set.of("host", "cookie", "content-length", "content-type",
            "transfer-encoding", "connection", "upgrade", "te", "trailer", "expect", "keep-alive", "proxy-authorization",
            "proxy-connection", "forwarded", "x-forwarded-for", "x-forwarded-host", "x-forwarded-proto");

    public static final ApiAuth NONE = new ApiAuth(Kind.NONE, null, null);
    public static final ApiAuth BEARER = new ApiAuth(Kind.BEARER, null, null);
    public static final ApiAuth BASIC = new ApiAuth(Kind.BASIC, null, null);

    public ApiAuth {
        Objects.requireNonNull(kind, "kind");
        if (kind == Kind.API_KEY) {
            if (place == null) {
                throw new InvalidIntegration("key-place-required", "Say whether the key goes in a header or the address.");
            }
            name = name == null ? "" : name.strip();
            if (!NAME.matcher(name).matches()) {
                throw new InvalidIntegration("key-name-invalid",
                        "Give the key's name: letters, digits, dots, dashes or underscores, up to 64.");
            }
            if (place == Place.HEADER && RESERVED_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                throw new InvalidIntegration("key-name-reserved", name + " can't carry a key. Use the header the API asks for.");
            }
        } else {
            place = null;
            name = null;
        }
    }

    public static ApiAuth apiKey(Place place, String name) {
        return new ApiAuth(Kind.API_KEY, place, name);
    }

    /** Whether a secret goes with this sign-in. */
    public boolean hasSecret() {
        return kind != Kind.NONE;
    }

    /** The header this sign-in sets, lower case, or null: a tool's input can't set it. */
    public String header() {
        return switch (kind) {
            case API_KEY -> place == Place.HEADER ? name.toLowerCase(Locale.ROOT) : null;
            case BEARER, BASIC -> "authorization";
            case NONE -> null;
        };
    }
}
