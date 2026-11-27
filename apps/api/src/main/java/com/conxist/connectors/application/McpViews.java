package com.conxist.connectors.application;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.conxist.connectors.domain.McpAuth;
import com.conxist.connectors.domain.McpServer;

/** What the app shows of MCP servers and connections. Credentials never appear here. */
public final class McpViews {

    private McpViews() {
    }

    /**
     * @param usedBy the agents that use the tool in a draft or live version; while there are any it stays allowed
     */
    public record ToolView(String name, String title, String description, boolean readOnly, boolean allowed,
            List<AgentUseView> usedBy) {
    }

    /** @param where DRAFT, LIVE or BOTH */
    public record AgentUseView(UUID agentId, String agentName, String where, boolean archived) {
    }

    /**
     * @param icon the logo to show: the one a person chose, else the one ConXist found
     * @param logo where the chosen logo came from, or null when it is the found one
     */
    public record ServerView(UUID id, String name, URI address, McpAuth auth, String icon, LogoView logo,
            McpServer.Status status, List<ToolView> tools, Instant addedAt, Instant checkedAt) {
    }

    /** A logo a person chose: the address ConXist fetched it from, once, and when. */
    public record LogoView(URI address, Instant fetchedAt) {
    }

    /**
     * @param signInUrl     where the person signs in, while {@code status} is SIGNING_IN
     * @param suggestedName the name the server gives itself, or its host
     * @param error         why it failed, while {@code status} is FAILED: sign-in-refused, unreachable, not-mcp
     */
    public record ConnectionView(UUID id, UUID serverId, McpConnections.Status status, URI signInUrl, McpAuth auth,
            String suggestedName, String icon, List<ToolView> tools, String error) {
    }

    /**
     * @param works    whether the server answered with its tools
     * @param error    why not: sign-in-needed, unreachable or not-mcp; null when it works
     * @param newTools tools the server lists now that it did not before; they start off
     */
    public record CheckView(boolean works, String error, List<String> newTools, ServerView server) {
    }
}
