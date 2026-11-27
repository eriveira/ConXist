package com.conxist.connectors.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.conxist.agents.AgentBriefs;
import com.conxist.connectors.domain.Integration;

/** What the app shows of integrations. Tokens never appear here, only their last 4 characters. */
public final class IntegrationViews {

    private final McpServerViews mcp;

    public IntegrationViews(McpServerViews mcp) {
        this.mcp = mcp;
    }

    /** @param tools ConXist's tools for it, with the ones allowed and the agents that use each */
    public record IntegrationView(UUID id, Integration.Kind kind, String name, String account,
            List<String> repositories, String tokenEnding, Integration.Status status, List<McpViews.ToolView> tools,
            Instant addedAt, Instant checkedAt) {
    }

    /** What a token reaches, before anything is saved. */
    public record TokenCheckView(String account, List<String> repositories, List<McpViews.ToolView> tools) {
    }

    /**
     * A Google sign-in for Gmail being made (ADR 0021). The refresh token never appears.
     *
     * @param status    SIGNING_IN, READY or FAILED
     * @param signInUrl Google's page, while SIGNING_IN
     * @param labels    the mailbox's own labels, once READY
     * @param error     why it failed: sign-in-refused, scope-refused or unreachable
     */
    public record GmailSignInView(UUID id, GmailSignIns.Status status, java.net.URI signInUrl, String account,
            List<String> labels, List<McpViews.ToolView> tools, String error) {
    }

    /** @param error why not: sign-in-needed, refused, repositories-lost or unreachable; null when it works */
    public record CheckView(boolean works, String error, IntegrationView integration) {
    }

    IntegrationView view(Integration integration) {
        return view(integration, mcp.uses().getOrDefault(integration.id(), Map.of()));
    }

    IntegrationView view(Integration integration, Map<String, List<AgentBriefs.McpToolUse>> uses) {
        return new IntegrationView(integration.id(), integration.kind(), integration.name(), integration.account(),
                integration.repositories(), integration.tokenEnding(), integration.status(),
                integration.tools().stream().map(tool -> McpServerViews.tool(tool, uses.getOrDefault(tool.name(),
                        List.of()))).toList(), integration.addedAt(), integration.checkedAt());
    }

    Map<UUID, Map<String, List<AgentBriefs.McpToolUse>>> uses() {
        return mcp.uses();
    }

    List<AgentBriefs.McpToolUse> usesOf(UUID integrationId) {
        return mcp.usesOf(integrationId);
    }
}
