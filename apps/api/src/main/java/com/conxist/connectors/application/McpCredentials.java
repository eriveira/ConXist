package com.conxist.connectors.application;

import java.time.Clock;
import java.time.Duration;
import com.conxist.connectors.domain.McpAuth;
import com.conxist.connectors.domain.McpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The token to send to a server: refreshed first when the server's own sign-in is about to expire. */
public final class McpCredentials {

    private static final Logger log = LoggerFactory.getLogger(McpCredentials.class);
    /** A token that expires sooner than this is refreshed before it is sent. */
    static final Duration REFRESH_AHEAD = Duration.ofSeconds(60);

    private final McpServerRepository servers;
    private final McpGateway gateway;
    private final Clock clock;

    public McpCredentials(McpServerRepository servers, McpGateway gateway, Clock clock) {
        this.servers = servers;
        this.gateway = gateway;
        this.clock = clock;
    }

    /** The token, or null for a server that takes none. Throws {@link McpGateway.SignInRefused} when it can't refresh. */
    String bearer(McpServer server) {
        McpCredential credential = servers.credential(server.workspaceId(), server.id())
                .orElseThrow(() -> new McpGateway.SignInRefused("no credential"));
        if (credential.expiresBefore(clock.instant().plus(REFRESH_AHEAD))) {
            return refresh(server, credential);
        }
        return credential.token();
    }

    /** A fresh token after the server refused the one sent; throws when there is no way to get one. */
    String refreshed(McpServer server) {
        McpCredential credential = servers.credential(server.workspaceId(), server.id())
                .orElseThrow(() -> new McpGateway.SignInRefused("no credential"));
        return refresh(server, credential);
    }

    private String refresh(McpServer server, McpCredential credential) {
        if (credential.auth() != McpAuth.OAUTH || credential.refreshToken() == null || credential.signIn() == null) {
            throw new McpGateway.SignInRefused("cannot refresh");
        }
        McpGateway.Tokens tokens = gateway.refresh(credential.signIn(), credential.refreshToken());
        servers.saveCredential(server.workspaceId(), server.id(), McpCredential.signedIn(credential.signIn(),
                new McpGateway.Tokens(tokens.accessToken(),
                        tokens.refreshToken() == null ? credential.refreshToken() : tokens.refreshToken(),
                        tokens.expiresAt())));
        log.info("Workspace {} refreshed its sign-in to MCP server {}", server.workspaceId(), server.id());
        return tokens.accessToken();
    }
}
