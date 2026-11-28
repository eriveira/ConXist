package com.conxist.connectors.application;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import com.conxist.agents.AgentBriefs;
import com.conxist.connectors.domain.ApiAuth;
import com.conxist.connectors.domain.ApiTool;
import com.conxist.connectors.domain.Integration;
import com.conxist.connectors.domain.InvalidIntegration;
import com.conxist.connectors.domain.Logo;
import com.conxist.platform.Conflict;
import com.conxist.platform.CurrentWorkspace;
import com.conxist.platform.NotFound;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The APIs a workspace connected (ADR 0027): connect one by its address and sign-in, check it, import its OpenAPI
 * description or write tools by hand, try a tool once, tick the tools agents may use, set its logo, remove it. A tool
 * or an API agents use stays: the person removes it from those agents first.
 */
public final class ManageIntegrations {

    /** Who changed what, and when: the workspace, the API and the action; never a secret or what an API answered. */
    private static final Logger log = LoggerFactory.getLogger(ManageIntegrations.class);
    /** What Try it shows of an answer. */
    static final int MAX_TRY_CHARACTERS = 20_000;

    private final IntegrationRepository integrations;
    private final ApiGateway gateway;
    private final ApiConnections connections;
    private final ApiDescriptions descriptions;
    private final IntegrationViews views;
    private final CurrentWorkspace workspace;
    private final Clock clock;

    public ManageIntegrations(IntegrationRepository integrations, ApiGateway gateway, ApiDescriptions descriptions,
            IntegrationViews views, CurrentWorkspace workspace, Clock clock) {
        this.integrations = integrations;
        this.gateway = gateway;
        this.connections = new ApiConnections(gateway);
        this.descriptions = descriptions;
        this.views = views;
        this.workspace = workspace;
        this.clock = clock;
    }

    /**
     * What a person typed to connect an API.
     *
     * @param secret   the key, the token or the password; for an edit, null or empty keeps the stored one
     * @param username for a username and password only
     */
    public record Connection(String name, String address, ApiAuth auth, String username, String secret,
            String checkPath) {
    }

    public List<IntegrationViews.IntegrationView> list() {
        Map<UUID, Map<String, List<AgentBriefs.McpToolUse>>> uses = views.uses();
        return integrations.findAll(workspace.id()).stream()
                .map(integration -> views.view(integration, uses.getOrDefault(integration.id(), Map.of()))).toList();
    }

    public IntegrationViews.IntegrationView get(UUID integrationId) {
        return views.view(load(integrationId));
    }

    /** Checks the sign-in with the API, then saves it with no tools yet. */
    public IntegrationViews.IntegrationView add(Connection connection) {
        UUID workspaceId = workspace.id();
        URI address = connections.guarded(connection.address(), "address");
        String secret = ApiConnections.secret(connection, null);
        Instant now = clock.instant();
        Integration draft = new Integration(UUID.randomUUID(), workspaceId, connection.name(), address,
                connection.auth(), ApiConnections.account(connection), ApiConnections.ending(connection.auth(), secret),
                connection.checkPath(), Integration.Status.WORKS, null, null, null, List.of(), now, now);
        connections.refuseIfFails(draft, secret);
        Integration integration = draft.connected(draft.name(), address, draft.auth(), draft.account(), draft.ending(),
                draft.checkPath(), connections.icon(address), now);
        if (!integrations.insert(integration, secret)) {
            throw taken(integration.name());
        }
        log.info("Workspace {} added API {}", workspaceId, integration.id());
        return views.view(integration);
    }

    /** Checks a changed connection with the API, then saves it; its tools stay. */
    public IntegrationViews.IntegrationView edit(UUID integrationId, Connection connection) {
        Integration integration = load(integrationId);
        URI address = connections.guarded(connection.address(), "address");
        // The stored secret goes only to the address it was given for: another one, or a new kind of sign-in, needs it
        // typed again.
        String kept = ApiConnections.keepsSecret(integration, address, connection.auth())
                ? integrations.secret(integration.workspaceId(), integrationId).orElse(null) : null;
        String secret = ApiConnections.secret(connection, kept);
        Integration changed = integration.connected(connection.name(), address, connection.auth(),
                ApiConnections.account(connection), ApiConnections.ending(connection.auth(), secret),
                connection.checkPath(), address.getHost().equalsIgnoreCase(integration.address().getHost())
                        ? integration.icon() : connections.icon(address), clock.instant());
        connections.refuseIfFails(changed, secret);
        // The secret is saved whole each time: for Basic it holds the username too.
        if (!integrations.updateConnection(changed, secret)) {
            throw taken(changed.name());
        }
        log.info("Workspace {} changed the connection of API {}", integration.workspaceId(), integrationId);
        return views.view(changed);
    }

    /** Calls the check path now with the stored sign-in, and saves whether it works. */
    public IntegrationViews.CheckView check(UUID integrationId) {
        Integration integration = load(integrationId);
        ApiConnections.Outcome outcome = connections.check(integration, secretOf(integration));
        Integration.Status status = outcome.works() ? Integration.Status.WORKS
                : "sign-in-refused".equals(outcome.error()) ? Integration.Status.SIGN_IN_NEEDED
                        : Integration.Status.UNREACHABLE;
        Integration checked = integration.withStatus(status, clock.instant());
        integrations.update(checked);
        log.info("Workspace {} checked API {}: {}", integration.workspaceId(), integrationId,
                outcome.works() ? "works" : outcome.error());
        return new IntegrationViews.CheckView(outcome.works(), outcome.error(), outcome.status(), outcome.millis(),
                outcome.request(), views.view(checked));
    }

    /**
     * Reads an OpenAPI description, fetched from {@code address} or given as {@code text}, into the API's tools.
     * Tools it had from an earlier import are replaced, keeping their ticks; tools a person wrote stay, and so does
     * an imported tool agents use. On the first import the tools that read come ticked; later new ones start off.
     */
    public IntegrationViews.ImportView importDescription(UUID integrationId, String address, String text) {
        Integration integration = load(integrationId);
        URI from = null;
        String source = text;
        if (address != null && !address.isBlank()) {
            from = connections.guarded(address, "description");
            try {
                source = gateway.description(from);
            } catch (ApiGateway.Unreachable unreachable) {
                throw new InvalidIntegration("description-unreachable", "ConXist could not fetch that description."
                        + " Check the address, or upload the file instead.");
            }
        }
        if (source == null || source.isBlank()) {
            throw new InvalidIntegration("description-required", "Give the description's address or its file.");
        }
        if (source.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > ApiGateway.MAX_DESCRIPTION_BYTES) {
            throw new InvalidIntegration("description-too-big", "That description is over 2 MB.");
        }
        ApiDescriptions.Read read;
        try {
            read = descriptions.read(source, integration.address(), from, Integration.MAX_TOOLS);
        } catch (ApiDescriptions.Unreadable unreadable) {
            throw new InvalidIntegration("description-unreadable", "ConXist can't read this as an OpenAPI"
                    + " description: " + unreadable.getMessage());
        }
        boolean first = integration.description() == null && integration.tools().stream().noneMatch(ApiTool::imported);
        Set<String> used = views.usesOf(integrationId).stream().map(AgentBriefs.McpToolUse::tool)
                .collect(Collectors.toSet());
        ToolMerge.Merged merged = ToolMerge.merge(integration.tools(), read.tools(), used, first);
        List<ApiDescriptions.LeftOut> leftOut = new ArrayList<>(read.leftOut());
        merged.overflow().forEach(name -> leftOut.add(new ApiDescriptions.LeftOut(name, ToolMerge.FULL)));
        Integration changed = integration.withTools(merged.tools(), new Integration.Description(read.title(), read.version(),
                from, clock.instant()));
        integrations.update(changed);
        log.info("Workspace {} imported a description into API {} ({} tools, {} left out)", integration.workspaceId(),
                integrationId, read.tools().size(), leftOut.size());
        return new IntegrationViews.ImportView(read.tools().size(), merged.added(), merged.kept(), leftOut,
                views.view(changed));
    }

    /** Adds a tool a person wrote. One that changes things starts off. */
    public IntegrationViews.IntegrationView addTool(UUID integrationId, ApiTool tool) {
        Integration integration = load(integrationId);
        refuseSignInInputs(integration, tool);
        List<ApiTool> tools = new ArrayList<>(integration.tools());
        tools.add(new ApiTool(tool.name(), tool.section(), tool.method(), tool.path(), tool.description(),
                tool.params(), tool.reads(), false, false).withAllowed(tool.allowed() && tool.reads()));
        Integration changed = integration.withTools(tools);
        integrations.update(changed);
        log.info("Workspace {} added tool {} to API {}", integration.workspaceId(), tool.name(), integrationId);
        return views.view(changed);
    }

    /**
     * Saves a changed tool in place of {@code toolName}. A tool agents use keeps its name, and stays one that reads if
     * it read; a tool that now changes things is unticked, so it is allowed again only after the warning.
     */
    public IntegrationViews.IntegrationView updateTool(UUID integrationId, String toolName, ApiTool tool) {
        Integration integration = load(integrationId);
        ApiTool earlier = integration.tool(toolName).orElseThrow(() -> toolGone(toolName));
        refuseSignInInputs(integration, tool);
        List<AgentBriefs.McpToolUse> uses = views.usesOf(integrationId).stream()
                .filter(use -> use.tool().equals(toolName)).toList();
        ApiTool changed = new ApiTool(tool.name(), tool.section(), tool.method(), tool.path(), tool.description(),
                tool.params(), tool.reads(), false, false);
        boolean allowed = ToolMerge.keepsTick(earlier, changed);
        if (!uses.isEmpty() && (!changed.name().equals(toolName) || (earlier.allowed() && !allowed))) {
            throw new Conflict("integration.tool-in-use", agentNames(uses) + " use " + toolName + ". Remove it"
                    + " from them first to rename it, let it change things or change what it calls.");
        }
        List<ApiTool> tools = integration.tools().stream()
                .map(each -> each.name().equals(toolName) ? changed.withAllowed(allowed) : each).toList();
        Integration saved = integration.withTools(tools);
        integrations.update(saved);
        log.info("Workspace {} changed tool {} of API {}", integration.workspaceId(), toolName, integrationId);
        return views.view(saved);
    }

    /** Removes a tool no agent uses. */
    public IntegrationViews.IntegrationView removeTool(UUID integrationId, String toolName) {
        Integration integration = load(integrationId);
        integration.tool(toolName).orElseThrow(() -> toolGone(toolName));
        List<AgentBriefs.McpToolUse> uses = views.usesOf(integrationId).stream()
                .filter(use -> use.tool().equals(toolName)).toList();
        if (!uses.isEmpty()) {
            throw new Conflict("integration.tool-in-use", agentNames(uses) + " use " + toolName
                    + ". Remove it from them first.");
        }
        Integration changed = integration.withTools(integration.tools().stream()
                .filter(tool -> !tool.name().equals(toolName)).toList());
        integrations.update(changed);
        log.info("Workspace {} removed tool {} of API {}", integration.workspaceId(), toolName, integrationId);
        return views.view(changed);
    }

    /**
     * Sends one call of a tool, saved or not, with these inputs and the stored sign-in, for a person to see what the
     * API answers. Nothing is kept. A tool that changes things really changes them; the app warns first.
     */
    public IntegrationViews.TryView tryTool(UUID integrationId, ApiTool tool, Map<String, Object> inputs) {
        Integration integration = load(integrationId);
        String secret = secretOf(integration);
        ApiGateway.Request request;
        try {
            request = ApiRequests.build(integration, secret, tool, inputs);
        } catch (ApiRequests.Problem problem) {
            throw new InvalidIntegration("try-input", problem.getMessage());
        }
        ApiGateway.Answer answer;
        try {
            answer = gateway.send(request);
        } catch (ApiGateway.AddressRefused refused) {
            throw new InvalidIntegration("address-refused", "ConXist no longer calls this address: " + refused.code());
        } catch (ApiGateway.Unreachable unreachable) {
            throw new InvalidIntegration("unreachable", integration.name() + " did not answer. Try again in a minute.");
        }
        log.info("Workspace {} tried tool {} of API {}: HTTP {}", integration.workspaceId(), tool.name(),
                integrationId, answer.status());
        return new IntegrationViews.TryView(answer.status(), answer.contentType(), answer.bytes(),
                answer.took().toMillis(), ApiRequests.shown(request), answer.text() == null ? null
                        : cut(ApiRequests.redacted(answer.text(), secret), MAX_TRY_CHARACTERS));
    }

    /** Allows exactly the ticked tools. A tool agents use can't be unticked. */
    public IntegrationViews.IntegrationView allow(UUID integrationId, Set<String> allowed) {
        Integration integration = load(integrationId);
        Set<String> unticked = integration.tools().stream().filter(ApiTool::allowed).map(ApiTool::name)
                .filter(tool -> !allowed.contains(tool)).collect(Collectors.toSet());
        List<AgentBriefs.McpToolUse> blocking = views.usesOf(integrationId).stream()
                .filter(use -> unticked.contains(use.tool())).toList();
        if (!blocking.isEmpty()) {
            throw new Conflict("integration.tool-in-use", "An agent uses " + blocking.getFirst().tool()
                    + ". Remove it from " + agentNames(blocking) + " first.");
        }
        Integration changed = integration.allow(allowed);
        integrations.update(changed);
        log.info("Workspace {} changed API {} ({} tools allowed)", integration.workspaceId(), integrationId,
                allowed.size());
        return views.view(changed);
    }

    /** Shows the image at this address as the API's logo; ConXist fetches it once, without the secret. */
    public IntegrationViews.IntegrationView useLogo(UUID integrationId, String address) {
        Integration integration = load(integrationId);
        URI at = connections.guarded(address, "logo");
        String image;
        try {
            image = gateway.image(at);
        } catch (ApiGateway.Unreachable unreachable) {
            throw new InvalidIntegration("logo-unreachable", "ConXist could not reach this address. Check it and try"
                    + " again.");
        }
        if (image == null) {
            throw new InvalidIntegration("logo-not-image",
                    "That address isn't an image. Use the address of a PNG, JPG, SVG, WebP or ICO up to 100 KB.");
        }
        return saveLogo(integration, new Logo(image, at, clock.instant()));
    }

    /** Shows an uploaded file as the API's logo. */
    public IntegrationViews.IntegrationView uploadLogo(UUID integrationId, String dataUrl) {
        return saveLogo(load(integrationId), Logo.uploaded(dataUrl, clock.instant()));
    }

    /** Goes back to the logo ConXist found for the API's site. */
    public IntegrationViews.IntegrationView dropLogo(UUID integrationId) {
        return saveLogo(load(integrationId), null);
    }

    /** The agents that use the API's tools; while there are any, it can't be removed. */
    public List<McpViews.AgentUseView> uses(UUID integrationId) {
        load(integrationId);
        Map<UUID, AgentBriefs.McpToolUse> byAgent = new LinkedHashMap<>();
        views.usesOf(integrationId).forEach(use -> byAgent.putIfAbsent(use.agentId(), use));
        return byAgent.values().stream().map(use -> new McpViews.AgentUseView(use.agentId(), use.agentName(),
                use.where(), use.archived())).toList();
    }

    public void remove(UUID integrationId) {
        Integration integration = load(integrationId);
        List<AgentBriefs.McpToolUse> uses = views.usesOf(integrationId);
        if (!uses.isEmpty()) {
            throw new Conflict("integration.in-use", "Agents use " + integration.name() + "'s tools. Remove them from "
                    + agentNames(uses) + " first.");
        }
        integrations.delete(integration.workspaceId(), integrationId);
        log.info("Workspace {} removed API {}", integration.workspaceId(), integrationId);
    }

    private IntegrationViews.IntegrationView saveLogo(Integration integration, Logo logo) {
        Integration changed = integration.withLogo(logo);
        integrations.update(changed);
        log.info("Workspace {} {} the logo of API {}", integration.workspaceId(), logo == null ? "reset" : "set",
                integration.id());
        return views.view(changed);
    }

    private String secretOf(Integration integration) {
        if (!integration.auth().hasSecret()) {
            return null;
        }
        return integrations.secret(integration.workspaceId(), integration.id()).orElseThrow(ManageIntegrations::gone);
    }

    /** ConXist sets the sign-in's key itself: a tool written by hand can't take it as an input. */
    private static void refuseSignInInputs(Integration integration, ApiTool tool) {
        tool.params().stream().filter(integration.auth()::sets).findFirst().ifPresent(param -> {
            throw new InvalidIntegration("tool-input-sign-in", param.name() + " is where ConXist puts "
                    + integration.name() + "'s sign-in. Remove that input: ConXist sends it on every call.");
        });
    }

    private Integration load(UUID integrationId) {
        return integrations.find(workspace.id(), integrationId).orElseThrow(ManageIntegrations::gone);
    }

    private static NotFound gone() {
        return new NotFound("integration.not-found", "This API is no longer in this workspace.");
    }

    private static NotFound toolGone(String name) {
        return new NotFound("integration.tool-not-found", "This API no longer has a tool called " + name + ".");
    }

    private static String agentNames(List<AgentBriefs.McpToolUse> uses) {
        Set<String> names = new LinkedHashSet<>();
        uses.forEach(use -> names.add(use.agentName()));
        return String.join(", ", names);
    }

    private static InvalidIntegration taken(String name) {
        return new InvalidIntegration("name-taken", "This workspace already has an API called " + name + ".");
    }

    private static String cut(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max) + "\n(The answer was cut here.)";
    }
}
