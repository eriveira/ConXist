package com.conxist.connectors.application;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Connections being made: from the address the person typed until the server is added, or for a server that signs in
 * again. They live in memory for {@link #KEPT_FOR} only, and are lost on restart: the person starts again. A token
 * here is in the API's memory only, never stored, until the server is added.
 */
public final class McpConnections {

    public static final Duration KEPT_FOR = Duration.ofMinutes(15);
    /** Bounds memory when many connections are started and never finished. */
    static final int MAX_KEPT = 200;

    private final Map<UUID, Connection> connections = new ConcurrentHashMap<>();
    private final Clock clock;

    public McpConnections(Clock clock) {
        this.clock = clock;
    }

    public enum Status {
        /** Waiting for the person to sign in on the server's page. */
        SIGNING_IN,
        /** The server answered with its tools; the person picks the tools and adds it. */
        READY,
        /** The server wants a token and offers no sign-in ConXist can use: the person pastes one. */
        TOKEN_NEEDED,
        /** The sign-in did not finish; {@code error} says why. */
        FAILED
    }

    /**
     * @param serverId the server that signs in again, or null for a new one
     * @param state    the OAuth state that comes back to the callback, for {@link Status#SIGNING_IN}
     * @param error    a code such as {@code sign-in-refused}, for {@link Status#FAILED}
     */
    public record Connection(UUID id, UUID workspaceId, UUID serverId, URI address, Status status,
            McpGateway.SignIn signIn, String state, String verifier, URI signInUrl, McpCredential credential,
            McpGateway.ServerFacts facts, String error, Instant startedAt) {

        Connection ready(McpCredential newCredential, McpGateway.ServerFacts newFacts) {
            return new Connection(id, workspaceId, serverId, address, Status.READY, signIn, null, null, null,
                    newCredential, newFacts, null, startedAt);
        }

        Connection failed(String code) {
            return new Connection(id, workspaceId, serverId, address, Status.FAILED, signIn, null, null, null, null,
                    null, code, startedAt);
        }

        @Override
        public String toString() {
            return "Connection[" + id + ", " + status + "]";
        }
    }

    Connection save(Connection connection) {
        forgetOld();
        connections.put(connection.id(), connection);
        return connection;
    }

    Optional<Connection> find(UUID workspaceId, UUID id) {
        return Optional.ofNullable(connections.get(id)).filter(this::fresh)
                .filter(connection -> connection.workspaceId().equals(workspaceId));
    }

    /** The connection waiting for this OAuth state, which comes back only once. */
    Optional<Connection> signingIn(String state) {
        if (state == null || state.isEmpty()) {
            return Optional.empty();
        }
        return connections.values().stream().filter(this::fresh)
                .filter(connection -> connection.status() == Status.SIGNING_IN && state.equals(connection.state()))
                .findFirst();
    }

    void forget(UUID id) {
        connections.remove(id);
    }

    Instant now() {
        return clock.instant();
    }

    private boolean fresh(Connection connection) {
        return connection.startedAt().plus(KEPT_FOR).isAfter(clock.instant());
    }

    private void forgetOld() {
        connections.values().removeIf(connection -> !fresh(connection));
        while (connections.size() >= MAX_KEPT) {
            connections.values().stream().min(Comparator.comparing(Connection::startedAt))
                    .ifPresent(oldest -> connections.remove(oldest.id()));
        }
    }
}
