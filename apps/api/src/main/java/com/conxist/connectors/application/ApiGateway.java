package com.conxist.connectors.application;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import com.conxist.connectors.domain.ApiTool;

/**
 * Talks to custom APIs (ADR 0027). Every address it calls is https and on the public internet, checked again on each
 * call; it follows no redirects, so a secret never reaches another host. Anything an API answers is untrusted.
 */
public interface ApiGateway {

    /**
     * The address as ConXist will call it: https, public, without a query, a fragment or a trailing slash.
     *
     * @throws AddressRefused when it is not one ConXist calls
     */
    URI checked(String address);

    /** Sends one request and reads up to 1 MB of the answer. */
    Answer send(Request request);

    /** Fetches an OpenAPI description, up to {@link #MAX_DESCRIPTION_BYTES}, as text. No secret is sent. */
    String description(URI address);

    /** The logo of the address's site, as a {@code data:image/...} address, or null when it has none. */
    String siteIcon(URI address);

    /** The image at this address as a {@code data:image/...} address, or null when it is not a small image. */
    String image(URI address);

    int MAX_DESCRIPTION_BYTES = 2 * 1024 * 1024;

    /**
     * @param url     the whole address, query included
     * @param headers names and values, the sign-in's included; never logged
     * @param body    the JSON object to send, or null for none
     */
    record Request(ApiTool.Method method, URI url, List<Map.Entry<String, String>> headers, Map<String, Object> body) {
    }

    /**
     * @param contentType the type the API gave, without parameters, or empty
     * @param text        the answer as text when it is text, JSON or XML; null otherwise
     * @param bytes       how many bytes ConXist read
     */
    record Answer(int status, String contentType, String text, long bytes, Duration took) {

        public boolean ok() {
            return status >= 200 && status < 300;
        }
    }

    /** The address is not one ConXist calls: address-invalid, address-not-https or address-private. */
    final class AddressRefused extends RuntimeException {
        private final String code;

        public AddressRefused(String code) {
            super(code);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    /** Network, timeouts, an answer too slow or too large. */
    final class Unreachable extends RuntimeException {
        public Unreachable(String reason) {
            super(reason);
        }
    }
}
