package com.conxist.capture.adapter.plaud;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * A short-lived RS256 JWT that proves ConXist holds the private key registered for a Plaud Client ID
 * (RFC 7523 client authentication). Single use: every assertion gets a fresh {@code jti}.
 */
final class ClientAssertion {

    static final Duration LIFETIME = Duration.ofSeconds(120);
    private static final Base64.Encoder URL = Base64.getUrlEncoder().withoutPadding();

    private ClientAssertion() {
    }

    static String sign(String clientId, String keyId, String audience, Instant now, PrivateKey key) {
        String header = json("alg", "RS256", "typ", "JWT", "kid", keyId);
        String payload = "{" + field("iss", clientId) + "," + field("sub", clientId) + "," + field("aud", audience)
                + "," + field("jti", UUID.randomUUID().toString())
                + ",\"iat\":" + now.getEpochSecond() + ",\"exp\":" + now.plus(LIFETIME).getEpochSecond() + "}";
        String signingInput = encode(header) + "." + encode(payload);
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(key);
            signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            return signingInput + "." + URL.encodeToString(signature.sign());
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("Could not sign the Plaud client assertion", failure);
        }
    }

    private static String json(String... pairs) {
        StringBuilder out = new StringBuilder("{");
        for (int i = 0; i < pairs.length; i += 2) {
            out.append(i == 0 ? "" : ",").append(field(pairs[i], pairs[i + 1]));
        }
        return out.append('}').toString();
    }

    private static String field(String name, String value) {
        return quote(name) + ":" + quote(value);
    }

    private static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                default -> {
                    if (c < 0x20) {
                        out.append("\\u%04x".formatted((int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    private static String encode(String json) {
        return URL.encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
