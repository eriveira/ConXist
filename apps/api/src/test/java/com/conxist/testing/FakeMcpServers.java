package com.conxist.testing;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The three fake MCP servers, one per sign-in mode; their addresses are logged so a person can add them. */
public record FakeMcpServers(FakeMcpServer oauth, FakeMcpServer token, FakeMcpServer open) implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(FakeMcpServers.class);

    public static FakeMcpServers start() throws IOException {
        FakeMcpServers servers = new FakeMcpServers(new FakeMcpServer(FakeMcpServer.Mode.OAUTH),
                new FakeMcpServer(FakeMcpServer.Mode.TOKEN), new FakeMcpServer(FakeMcpServer.Mode.PUBLIC));
        log.info("Fake MCP servers: sign-in {}, token {} (token {}), public {}", servers.oauth().mcpUrl(),
                servers.token().mcpUrl(), FakeMcpServer.TOKEN, servers.open().mcpUrl());
        return servers;
    }

    @Override
    public void close() {
        oauth.close();
        token.close();
        open.close();
    }
}
