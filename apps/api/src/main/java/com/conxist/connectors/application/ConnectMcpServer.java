package com.conxist.connectors.application;

import java.net.URI;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import com.conxist.connectors.application.McpConnections.Connection;
import com.conxist.connectors.application.McpConnections.Status;
import com.conxist.connectors.domain.InvalidMcp;
import com.conxist.connectors.domain.McpServer;
import com.conxist.platform.CurrentWorkspace;
import com.conxist.platform.NotFound;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Connects to an MCP server from its address (ADR 0017). ConXist finds how to sign in itself: it calls the server
 * without a credential, and when the server asks for one it uses the server's own sign-in (OAuth) if it offers one,
 * else it asks for a token. A pasted token is checked with the server before anything goes on.
 */
public final class ConnectMcpServer {

    private static final Logger log = LoggerFactory.getLogger(ConnectMcpServer.class);
    /** Tokens are long, but not this long; anything longer is not a token. */
    static final int MAX_TOKEN_LENGTH = 8_000;
    private static final Base64.Encoder URL_SAFE = Base64.getUrlEncoder().withoutPadding();

    private final McpGateway gateway;
    private final McpConnections connections;
    private final McpServerRepository servers;
    private final McpServerViews views;
    private final CurrentWorkspace workspace;
    private final SecureRandom random = new SecureRandom();

    public ConnectMcpServer(McpGateway gateway, McpConnections connections, McpServerRepository servers,
            McpServerViews views, CurrentWorkspace workspace) {
        this.gateway = gateway;
        this.connections = connections;
        this.servers = servers;
        this.views = views;
        this.workspace = workspace;
    }

    /**
     * Starts a connection to the address, or to an added server that signs in again ({@code serverId}).
     *
     * @param token a token the person pasted, or null to let ConXist find how to sign in
     */
    public McpViews.ConnectionView start(String address, String token, UUID serverId) {
        UUID workspaceId = workspace.id();
        URI uri = serverId == null ? checked(address) : server(workspaceId, serverId).address();
        Optional<String> pasted = pastedToken(token);
        Connection started = new Connection(UUID.randomUUID(), workspaceId, serverId, uri, Status.FAILED, null, null,
                null, null, null, null, null, connections.now());
        if (pasted.isPresent()) {
            McpGateway.ServerFacts facts = listWithToken(uri, pasted.get());
            log.info("Workspace {} connected to an MCP server on {} with a token", workspaceId, uri.getHost());
            return views.connection(connections.save(started.ready(McpCredential.pasted(pasted.get()), facts)));
        }
        try {
            McpGateway.ServerFacts facts = gateway.list(uri, null);
            log.info("Workspace {} connected to an MCP server on {} without sign-in", workspaceId, uri.getHost());
            return views.connection(connections.save(started.ready(McpCredential.none(), facts)));
        } catch (McpGateway.SignInRefused asks) {
            return signIn(started);
        } catch (McpGateway.NotMcp | McpGateway.Unreachable failure) {
            throw problem(failure);
        }
    }

    /** The connection as it is now; the app asks until the sign-in is done. */
    public McpViews.ConnectionView status(UUID connectionId) {
        return views.connection(connections.find(workspace.id(), connectionId).orElseThrow(ConnectMcpServer::gone));
    }

    /** The server's sign-in page sends the person back here, with a code or an error. Unknown states do nothing. */
    public void finish(String state, String code, String error) {
        connections.signingIn(state).ifPresent(connection -> {
            if (error != null || code == null || code.isEmpty()) {
                log.info("Workspace {} did not finish signing in to an MCP server", connection.workspaceId());
                connections.save(connection.failed("sign-in-refused"));
                return;
            }
            try {
                McpGateway.Tokens tokens = gateway.exchange(connection.signIn(), code, connection.verifier());
                McpGateway.ServerFacts facts = gateway.list(connection.address(), tokens.accessToken());
                connections.save(connection.ready(McpCredential.signedIn(connection.signIn(), tokens), facts));
                log.info("Workspace {} signed in to an MCP server on {}", connection.workspaceId(),
                        connection.address().getHost());
            } catch (McpGateway.SignInRefused refused) {
                connections.save(connection.failed("sign-in-refused"));
            } catch (McpGateway.NotMcp notMcp) {
                connections.save(connection.failed("not-mcp"));
            } catch (McpGateway.Unreachable | McpGateway.AddressRefused unreachable) {
                connections.save(connection.failed("unreachable"));
            }
        });
    }

    private McpViews.ConnectionView signIn(Connection started) {
        Optional<McpGateway.SignIn> signIn;
        try {
            signIn = gateway.signInFor(started.address());
        } catch (McpGateway.Unreachable | McpGateway.AddressRefused | McpGateway.SignInRefused | McpGateway.NotMcp none) {
            signIn = Optional.empty();
        }
        if (signIn.isEmpty()) {
            return views.connection(connections.save(new Connection(started.id(), started.workspaceId(),
                    started.serverId(), started.address(), Status.TOKEN_NEEDED, null, null, null, null, null, null,
                    null, started.startedAt())));
        }
        String state = secret();
        String verifier = secret();
        URI url = gateway.authorizationUrl(signIn.get(), state, verifier);
        return views.connection(connections.save(new Connection(started.id(), started.workspaceId(),
                started.serverId(), started.address(), Status.SIGNING_IN, signIn.get(), state, verifier, url, null,
                null, null, started.startedAt())));
    }

    private McpGateway.ServerFacts listWithToken(URI uri, String token) {
        try {
            return gateway.list(uri, token);
        } catch (McpGateway.SignInRefused refused) {
            throw new InvalidMcp("token-refused",
                    "The server refused this token. Check that it is active and copied in full.");
        } catch (McpGateway.NotMcp | McpGateway.Unreachable failure) {
            throw problem(failure);
        }
    }

    private URI checked(String address) {
        try {
            return gateway.checked(address == null ? "" : address.strip());
        } catch (McpGateway.Unreachable unknownHost) {
            throw problem(unknownHost);
        } catch (McpGateway.AddressRefused refused) {
            throw switch (refused.code()) {
                case "address-not-https" -> new InvalidMcp("address-not-https",
                        "Use an address that starts with https://.");
                case "address-private" -> new InvalidMcp("address-private",
                        "This address is on a private network. ConXist only connects to servers on the internet.");
                default -> new InvalidMcp("address-invalid", "This does not look like a server address.");
            };
        }
    }

    private McpServer server(UUID workspaceId, UUID serverId) {
        return servers.find(workspaceId, serverId)
                .orElseThrow(() -> new NotFound("mcp.not-found", "This MCP server is no longer in this workspace."));
    }

    private static Optional<String> pastedToken(String token) {
        String value = token == null ? "" : token.strip();
        if (value.isEmpty()) {
            return Optional.empty();
        }
        if (value.length() > MAX_TOKEN_LENGTH || value.chars().anyMatch(c -> c <= ' ' || c == 127)) {
            throw new InvalidMcp("token-invalid", "This does not look like a token. Copy it again.");
        }
        return Optional.of(value);
    }

    static RuntimeException problem(RuntimeException failure) {
        if (failure instanceof McpGateway.NotMcp) {
            return new InvalidMcp("not-mcp", "This address answered, but not as an MCP server. Check the address.");
        }
        return new InvalidMcp("unreachable", "ConXist could not reach this server. Check the address and try again.");
    }

    private String secret() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return URL_SAFE.encodeToString(bytes);
    }

    private static NotFound gone() {
        return new NotFound("mcp.connection-gone", "This connection expired. Start again.");
    }
}
