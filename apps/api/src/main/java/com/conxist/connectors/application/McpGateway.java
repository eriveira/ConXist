package com.conxist.connectors.application;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.conxist.connectors.domain.McpTool;

/**
 * Talks to outside MCP servers and their sign-in (OAuth 2.1). Every address it calls is https and public; anything a
 * server answers is untrusted. Failures are the exceptions below, so a use case can say what went wrong.
 */
public interface McpGateway {

    /** The address as ConXist will call it; refused when it is not https or not on the public internet. */
    URI checked(String address);

    /**
     * Connects and lists what the server is and offers.
     *
     * @param bearer the token to send, or null to call without one
     */
    ServerFacts list(URI address, String bearer);

    /**
     * How to sign in to the server with its own sign-in page, when it offers one ConXist can use: its authorization
     * server's endpoints and a client registered for ConXist. Empty when the server offers no such sign-in.
     */
    Optional<SignIn> signInFor(URI address);

    /** Where the person signs in; the server sends them back to ConXist's callback with a code. */
    URI authorizationUrl(SignIn signIn, String state, String verifier);

    Tokens exchange(SignIn signIn, String code, String verifier);

    Tokens refresh(SignIn signIn, String refreshToken);

    /** Calls one tool and returns its text; {@code error} is true when the tool says it failed. */
    ToolAnswer call(URI address, String bearer, String tool, Map<String, Object> arguments);

    /**
     * @param name  the server's own name ({@code serverInfo.name})
     * @param title the name it shows people, when it gives one
     * @param icon  its logo as a {@code data:image/...} address, or null
     * @param tools the tools it lists that have a usable name, in its order
     */
    record ServerFacts(String name, String title, String icon, List<McpTool> tools) {
    }

    /**
     * @param resource the MCP server's address, sent as the resource indicator
     * @param scope    the scopes the server asks for, space-separated, or null
     */
    record SignIn(String issuer, URI authorizationEndpoint, URI tokenEndpoint, String clientId, URI resource,
            String scope) {
    }

    /** {@code refreshToken} may be null when the server issues none. */
    record Tokens(String accessToken, String refreshToken, Instant expiresAt) {
    }

    record ToolAnswer(String text, boolean error) {
    }

    /** The address is not one ConXist calls. The code says why: address-invalid, address-not-https, address-private. */
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

    /** The server or its sign-in refused the credential (or asked for one). */
    final class SignInRefused extends RuntimeException {
        public SignInRefused(String reason) {
            super(reason);
        }
    }

    /** The server answered, but not as an MCP server. */
    final class NotMcp extends RuntimeException {
        public NotMcp(String reason) {
            super(reason);
        }
    }

    /** Anything that may pass: network, timeouts, server errors. */
    final class Unreachable extends RuntimeException {
        public Unreachable(String reason) {
            super(reason);
        }
    }
}
