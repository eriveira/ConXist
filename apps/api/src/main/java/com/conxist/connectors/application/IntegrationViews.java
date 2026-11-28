package com.conxist.connectors.application;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.conxist.agents.AgentBriefs;
import com.conxist.connectors.domain.ApiAuth;
import com.conxist.connectors.domain.ApiParam;
import com.conxist.connectors.domain.ApiTool;
import com.conxist.connectors.domain.Integration;

/** What the app shows of APIs (ADR 0027). Secrets never appear here, only their last 4 characters. */
public final class IntegrationViews {

    private final McpServerViews mcp;

    public IntegrationViews(McpServerViews mcp) {
        this.mcp = mcp;
    }

    /**
     * @param icon the logo to show: the one a person chose, else the one ConXist found
     * @param logo the chosen logo's source, or null when it is the found one
     */
    public record IntegrationView(UUID id, String name, URI address, AuthView auth, String account, String ending,
            String checkPath, Integration.Status status, String icon, McpViews.LogoView logo,
            DescriptionView description, List<ToolView> tools, Instant addedAt, Instant checkedAt) {
    }

    public record AuthView(ApiAuth.Kind kind, ApiAuth.Place place, String name) {
    }

    public record DescriptionView(String title, String version, URI address, Instant importedAt) {
    }

    /** @param usedBy the agents that use it in a draft or live version; while there are any it stays allowed */
    public record ToolView(String name, String section, ApiTool.Method method, String path, String description,
            List<ParamView> params, boolean reads, boolean imported, boolean allowed,
            List<McpViews.AgentUseView> usedBy) {
    }

    /** @param type the schema's type, for the editor; the whole schema stays in {@code schema} */
    public record ParamView(String name, ApiParam.In in, String type, Map<String, Object> schema, boolean required,
            String description) {
    }

    /**
     * @param works  whether the API accepted the sign-in
     * @param error  why not: sign-in-refused, unreachable, http-NNN; null when it works
     * @param status the HTTP status it answered, or 0
     */
    public record CheckView(boolean works, String error, int status, long millis, String request,
            IntegrationView integration) {
    }

    /** What importing a description did. */
    public record ImportView(int found, List<String> added, List<String> kept, List<ApiDescriptions.LeftOut> leftOut,
            IntegrationView integration) {
    }

    /** One call a person sent from Try it; nothing of it is kept. */
    public record TryView(int status, String contentType, long bytes, long millis, String request, String text) {
    }

    IntegrationView view(Integration integration) {
        return view(integration, mcp.uses().getOrDefault(integration.id(), Map.of()));
    }

    IntegrationView view(Integration integration, Map<String, List<AgentBriefs.McpToolUse>> uses) {
        return new IntegrationView(integration.id(), integration.name(), integration.address(),
                new AuthView(integration.auth().kind(), integration.auth().place(), integration.auth().name()),
                integration.account(), integration.ending(), integration.checkPath(), integration.status(),
                integration.shownIcon(), integration.logo() == null ? null
                        : new McpViews.LogoView(integration.logo().address(), integration.logo().fetchedAt()),
                integration.description() == null ? null : new DescriptionView(integration.description().title(),
                        integration.description().version(), integration.description().address(),
                        integration.description().importedAt()),
                integration.tools().stream().map(tool -> tool(tool, uses.getOrDefault(tool.name(), List.of())))
                        .toList(), integration.addedAt(), integration.checkedAt());
    }

    static ToolView tool(ApiTool tool, List<AgentBriefs.McpToolUse> uses) {
        return new ToolView(tool.name(), tool.section(), tool.method(), tool.path(), tool.description(),
                tool.params().stream().map(IntegrationViews::param).toList(), tool.reads(), tool.imported(),
                tool.allowed(), uses.stream().map(use -> new McpViews.AgentUseView(use.agentId(), use.agentName(),
                        use.where(), use.archived())).toList());
    }

    private static ParamView param(ApiParam param) {
        Object type = param.schema().get("type");
        return new ParamView(param.name(), param.in(), type instanceof String text ? text : "string", param.schema(),
                param.required(), param.description());
    }

    Map<UUID, Map<String, List<AgentBriefs.McpToolUse>>> uses() {
        return mcp.uses();
    }

    List<AgentBriefs.McpToolUse> usesOf(UUID integrationId) {
        return mcp.usesOf(integrationId);
    }
}
