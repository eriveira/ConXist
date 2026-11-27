package com.conxist.connectors.application;

import java.time.Instant;
import com.conxist.connectors.domain.McpAuth;

/**
 * What ConXist sends to a server, kept only encrypted (ADR 0004): nothing, a pasted token, or the tokens of the
 * server's own sign-in with what it takes to refresh them. It never goes back to the app.
 *
 * @param token        the pasted token or the access token; null for {@link McpAuth#NONE}
 * @param refreshToken null when there is none
 * @param expiresAt    when the access token expires; null when it is not known to
 * @param signIn       how to refresh, for {@link McpAuth#OAUTH}
 */
public record McpCredential(McpAuth auth, String token, String refreshToken, Instant expiresAt,
        McpGateway.SignIn signIn) {

    public static McpCredential none() {
        return new McpCredential(McpAuth.NONE, null, null, null, null);
    }

    public static McpCredential pasted(String token) {
        return new McpCredential(McpAuth.TOKEN, token, null, null, null);
    }

    public static McpCredential signedIn(McpGateway.SignIn signIn, McpGateway.Tokens tokens) {
        return new McpCredential(McpAuth.OAUTH, tokens.accessToken(), tokens.refreshToken(), tokens.expiresAt(),
                signIn);
    }

    /** Whether the access token expires before {@code at}, so it is refreshed first. */
    boolean expiresBefore(Instant at) {
        return auth == McpAuth.OAUTH && expiresAt != null && expiresAt.isBefore(at);
    }

    @Override
    public String toString() {
        return "McpCredential[" + auth + "]";
    }
}
