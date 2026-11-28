package com.conxist.connectors.application;

import java.net.URI;
import com.conxist.connectors.domain.ApiAuth;
import com.conxist.connectors.domain.Integration;
import com.conxist.connectors.domain.InvalidIntegration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Connecting to a custom API (ADR 0027): the addresses ConXist may call, the secret a person typed, and the check that
 * the sign-in works before anything is saved.
 */
final class ApiConnections {

    private static final Logger log = LoggerFactory.getLogger(ApiConnections.class);
    static final int MAX_SECRET_LENGTH = 4_000;

    private final ApiGateway gateway;

    ApiConnections(ApiGateway gateway) {
        this.gateway = gateway;
    }

    /** How a check went, for the person: the request shown has no query, so a key in the address never shows. */
    record Outcome(boolean works, String error, int status, long millis, String request) {
    }

    /**
     * Calls the check path, or the address, with the sign-in. With a check path only a 2xx answer works; without one,
     * any answer but a refusal of the sign-in or a server error shows the address answers.
     */
    Outcome check(Integration integration, String secret) {
        ApiGateway.Request request = ApiRequests.check(integration, secret);
        String shown = ApiRequests.shown(request);
        try {
            ApiGateway.Answer answer = gateway.send(request);
            int status = answer.status();
            long millis = answer.took().toMillis();
            if (status == 401 || status == 403) {
                return new Outcome(false, "sign-in-refused", status, millis, shown);
            }
            boolean works = integration.checkPath() != null ? answer.ok() : status < 500;
            return new Outcome(works, works ? null : "http-" + status, status, millis, shown);
        } catch (ApiGateway.AddressRefused refused) {
            return new Outcome(false, refused.code(), 0, 0, shown);
        } catch (ApiGateway.Unreachable unreachable) {
            return new Outcome(false, "unreachable", 0, 0, shown);
        }
    }

    /** Checks the sign-in and refuses, in words the person can act on, when it doesn't work. */
    void refuseIfFails(Integration integration, String secret) {
        Outcome outcome = check(integration, secret);
        if (outcome.works()) {
            return;
        }
        throw switch (outcome.error()) {
            case "sign-in-refused" -> new InvalidIntegration("sign-in-refused", "The API refused the sign-in (HTTP "
                    + outcome.status() + "). Check the secret, and where it goes.");
            case "unreachable" -> new InvalidIntegration("unreachable", "The API did not answer. Check the address,"
                    + " then try again.");
            case "address-private" -> new InvalidIntegration("address-private", "This address is on a private"
                    + " network. ConXist only calls APIs on the internet.");
            case "address-not-https", "address-invalid" -> new InvalidIntegration("address-invalid",
                    "Use an https address on the internet.");
            default -> new InvalidIntegration("check-failed", outcome.request() + " answered HTTP " + outcome.status()
                    + ". Check the path, or leave it empty.");
        };
    }

    /** An https address on the internet; the code names what it is for: address, logo or description. */
    URI guarded(String value, String what) {
        try {
            return gateway.checked(value == null ? "" : value.strip());
        } catch (ApiGateway.AddressRefused refused) {
            throw switch (refused.code()) {
                case "address-not-https" -> new InvalidIntegration(what + "-not-https",
                        "Use an address that starts with https://.");
                case "address-private" -> new InvalidIntegration(what + "-private",
                        "This address is on a private network. ConXist only calls the internet.");
                default -> new InvalidIntegration(what + "-invalid", "This doesn't look like a web address.");
            };
        } catch (ApiGateway.Unreachable unknown) {
            throw new InvalidIntegration(what + "-unknown-host", "ConXist can't find this address's host.");
        }
    }

    /**
     * Whether a stored secret may go to {@code next}: only to the same scheme, host, port and path it was given for,
     * with the same kind of sign-in. A secret never goes to an address it wasn't given for.
     */
    static boolean keepsSecret(Integration before, URI next, ApiAuth nextAuth) {
        URI was = before.address();
        return next.getScheme().equalsIgnoreCase(was.getScheme()) && next.getHost().equalsIgnoreCase(was.getHost())
                && next.getPort() == was.getPort() && trimmed(next.getRawPath()).equals(trimmed(was.getRawPath()))
                && nextAuth.kind() == before.auth().kind();
    }

    /** The secret to seal: the key or token, or {@code username\npassword}; {@code kept} when none was typed. */
    static String secret(ManageIntegrations.Connection connection, String kept) {
        ApiAuth auth = connection.auth();
        if (!auth.hasSecret()) {
            return null;
        }
        String typed = connection.secret() == null ? "" : connection.secret();
        if (auth.kind() == ApiAuth.Kind.BASIC) {
            String username = connection.username() == null ? "" : connection.username().strip();
            if (username.isEmpty() || username.contains("\n") || username.contains(":")) {
                throw new InvalidIntegration("username-invalid", "Enter the username, without : or line breaks.");
            }
            String password = typed.isEmpty() && kept != null ? kept.substring(kept.indexOf('\n') + 1) : typed;
            return username + "\n" + required(password);
        }
        String value = typed.strip().isEmpty() ? kept : typed.strip();
        return required(value);
    }

    static String account(ManageIntegrations.Connection connection) {
        return connection.auth().kind() == ApiAuth.Kind.BASIC && connection.username() != null
                ? connection.username().strip() : null;
    }

    static String ending(ApiAuth auth, String secret) {
        if (secret == null) {
            return null;
        }
        String part = auth.kind() == ApiAuth.Kind.BASIC ? secret.substring(secret.indexOf('\n') + 1) : secret;
        return Integration.endingOf(part);
    }

    /** The icon the address's site shows, or null. */
    String icon(URI address) {
        try {
            return gateway.siteIcon(address);
        } catch (RuntimeException unusable) {
            log.debug("No site icon for an API: {}", unusable.getClass().getSimpleName());
            return null;
        }
    }

    private static String required(String secret) {
        if (secret == null || secret.isEmpty()) {
            throw new InvalidIntegration("secret-required", "Paste the secret first.");
        }
        if (secret.length() > MAX_SECRET_LENGTH || secret.chars().anyMatch(c -> c == '\r' || c == '\n' || c == 0)) {
            throw new InvalidIntegration("secret-invalid", "That doesn't look like a key or a token. Copy it again.");
        }
        return secret;
    }

    private static String trimmed(String path) {
        return path == null ? "" : path.replaceAll("/+$", "");
    }
}
