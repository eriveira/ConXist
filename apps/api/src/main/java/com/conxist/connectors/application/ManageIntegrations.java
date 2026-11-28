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
    static final int MAX_SECRET_LENGTH = 4_000;

    private final IntegrationRepository integrations;
    private final ApiGateway gateway;
    private final ApiDescriptions descriptions;
    private final IntegrationViews views;
    private final CurrentWorkspace workspace;
    private final Clock clock;

    public ManageIntegrations(IntegrationRepository integrations, ApiGateway gateway, ApiDescriptions descriptions,
            IntegrationViews views, CurrentWorkspace workspace, Clock clock) {
        this.integrations = integrations;
        this.gateway = gateway;
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
        URI address = address(connection.address());
        String secret = secret(connection, null);
        Instant now = clock.instant();
        Integration draft = new Integration(UUID.randomUUID(), workspaceId, connection.name(), address,
                connection.auth(), account(connection), ending(connection.auth(), secret), connection.checkPath(),
                Integration.Status.WORKS, null, null, null, List.of(), now, now);
        refuseIfFails(check(draft, secret));
        Integration integration = draft.connected(draft.name(), address, draft.auth(), draft.account(), draft.ending(),
                draft.checkPath(), icon(address), now);
        if (!integrations.insert(integration, secret)) {
            throw taken(integration.name());
        }
        log.info("Workspace {} added API {}", workspaceId, integration.id());
        return views.view(integration);
    }

    /** Checks a changed connection with the API, then saves it; its tools stay. */
    public IntegrationViews.IntegrationView edit(UUID integrationId, Connection connection) {
        Integration integration = load(integrationId);
        URI address = address(connection.address());
        // The stored secret goes only to the host it was given for: a new host, or a new kind of sign-in, needs it again.
        boolean sameHost = address.getHost().equalsIgnoreCase(integration.address().getHost())
                && address.getPort() == integration.address().getPort()
                && address.getScheme().equals(integration.address().getScheme());
        String kept = sameHost && connection.auth().kind() == integration.auth().kind()
                ? integrations.secret(integration.workspaceId(), integrationId).orElse(null) : null;
        String secret = secret(connection, kept);
        Integration changed = integration.connected(connection.name(), address, connection.auth(),
                account(connection), ending(connection.auth(), secret), connection.checkPath(),
                address.getHost().equals(integration.address().getHost()) ? integration.icon() : icon(address),
                clock.instant());
        refuseIfFails(check(changed, secret));
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
        Outcome outcome = check(integration, secretOf(integration));
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
            from = guarded(address, "description");
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
        if (source.length() > ApiGateway.MAX_DESCRIPTION_BYTES) {
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
        Map<String, ApiTool> before = new LinkedHashMap<>();
        integration.tools().forEach(tool -> before.put(tool.name(), tool));
        Set<String> used = views.usesOf(integrationId).stream().map(AgentBriefs.McpToolUse::tool)
                .collect(Collectors.toSet());
        List<ApiTool> merged = new ArrayList<>();
        List<String> added = new ArrayList<>();
        Set<String> names = new LinkedHashSet<>();
        for (ApiTool tool : read.tools()) {
            ApiTool earlier = before.get(tool.name());
            if (earlier != null && !earlier.imported()) {
                continue;
            }
            boolean allowed = earlier != null ? earlier.allowed() && (tool.reads() || !earlier.reads())
                    : first && tool.reads();
            if (earlier == null) {
                added.add(tool.name());
            }
            merged.add(tool.withAllowed(allowed));
            names.add(tool.name());
        }
        List<String> kept = new ArrayList<>();
        for (ApiTool tool : integration.tools()) {
            boolean stays = !tool.imported() || used.contains(tool.name());
            if (stays && !names.contains(tool.name())) {
                merged.add(tool);
                if (tool.imported()) {
                    kept.add(tool.name());
                }
            }
        }
        if (merged.size() > Integration.MAX_TOOLS) {
            merged = new ArrayList<>(merged.subList(0, Integration.MAX_TOOLS));
        }
        Integration changed = integration.withTools(merged, new Integration.Description(read.title(), read.version(),
                from, clock.instant()));
        integrations.update(changed);
        log.info("Workspace {} imported a description into API {} ({} tools, {} left out)", integration.workspaceId(),
                integrationId, read.tools().size(), read.leftOut().size());
        return new IntegrationViews.ImportView(read.tools().size(), added, kept, read.leftOut(), views.view(changed));
    }

    /** Adds a tool a person wrote. One that changes things starts off. */
    public IntegrationViews.IntegrationView addTool(UUID integrationId, ApiTool tool) {
        Integration integration = load(integrationId);
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
        List<AgentBriefs.McpToolUse> uses = views.usesOf(integrationId).stream()
                .filter(use -> use.tool().equals(toolName)).toList();
        ApiTool changed = new ApiTool(tool.name(), tool.section(), tool.method(), tool.path(), tool.description(),
                tool.params(), tool.reads(), false, false);
        if (!uses.isEmpty() && (!changed.name().equals(toolName) || (earlier.reads() && !changed.reads()))) {
            throw new Conflict("integration.tool-in-use", agentNames(uses) + " use " + toolName + ". Remove it"
                    + " from them first to rename it or let it change things.");
        }
        boolean allowed = earlier.allowed() && (changed.reads() || !earlier.reads());
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
        ApiGateway.Request request;
        try {
            request = ApiRequests.build(integration, secretOf(integration), tool, inputs);
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
                        : cut(answer.text(), MAX_TRY_CHARACTERS));
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
        URI at = guarded(address, "logo");
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

    /** How a check went, for the person: the request shown has no query, so a key in the address never shows. */
    private record Outcome(boolean works, String error, int status, long millis, String request) {
    }

    /**
     * Calls the check path, or the address, with the sign-in. With a check path only a 2xx answer works; without one,
     * any answer but a refusal of the sign-in or a server error shows the address answers.
     */
    private Outcome check(Integration integration, String secret) {
        ApiGateway.Request request = ApiRequests.check(integration, secret);
        String shown = ApiRequests.shown(request);
        try {
            ApiGateway.Answer answer = gateway.send(request);
            int status = answer.status();
            long millis = answer.took().toMillis();
            if (status == 401 || status == 403) {
                return new Outcome(false, "sign-in-refused", status, millis, shown);
            }
            boolean works = integration.checkPath() != null ? answer.ok() : status < 500;
            return new Outcome(works, works ? null : "http-" + status, status, millis, shown);
        } catch (ApiGateway.AddressRefused refused) {
            return new Outcome(false, refused.code(), 0, 0, shown);
        } catch (ApiGateway.Unreachable unreachable) {
            return new Outcome(false, "unreachable", 0, 0, shown);
        }
    }

    private static void refuseIfFails(Outcome outcome) {
        if (outcome.works()) {
            return;
        }
        throw switch (outcome.error()) {
            case "sign-in-refused" -> new InvalidIntegration("sign-in-refused", "The API refused the sign-in (HTTP "
                    + outcome.status() + "). Check the secret, and where it goes.");
            case "unreachable" -> new InvalidIntegration("unreachable", "The API did not answer. Check the address,"
                    + " then try again.");
            case "address-private" -> new InvalidIntegration("address-private", "This address is on a private"
                    + " network. ConXist only calls APIs on the internet.");
            case "address-not-https", "address-invalid" -> new InvalidIntegration("address-invalid",
                    "Use an https address on the internet.");
            default -> new InvalidIntegration("check-failed", outcome.request() + " answered HTTP " + outcome.status()
                    + ". Check the path, or leave it empty.");
        };
    }

    private URI address(String value) {
        return guarded(value, "address");
    }

    /** An https address on the internet; the code names what it is for: address, logo or description. */
    private URI guarded(String value, String what) {
        try {
            return gateway.checked(value == null ? "" : value.strip());
        } catch (ApiGateway.AddressRefused refused) {
            throw switch (refused.code()) {
                case "address-not-https" -> new InvalidIntegration(what + "-not-https",
                        "Use an address that starts with https://.");
                case "address-private" -> new InvalidIntegration(what + "-private",
                        "This address is on a private network. ConXist only calls the internet.");
                default -> new InvalidIntegration(what + "-invalid", "This doesn't look like a web address.");
            };
        } catch (ApiGateway.Unreachable unknown) {
            throw new InvalidIntegration(what + "-unknown-host", "ConXist can't find this address's host.");
        }
    }

    /** The secret to seal: the key or token, or {@code username\npassword}; {@code kept} when none was typed. */
    private static String secret(Connection connection, String kept) {
        ApiAuth auth = connection.auth();
        if (!auth.hasSecret()) {
            return null;
        }
        String typed = connection.secret() == null ? "" : connection.secret();
        if (auth.kind() == ApiAuth.Kind.BASIC) {
            String username = connection.username() == null ? "" : connection.username().strip();
            if (username.isEmpty() || username.contains("\n") || username.contains(":")) {
                throw new InvalidIntegration("username-invalid", "Enter the username, without : or line breaks.");
            }
            String password = typed.isEmpty() && kept != null ? kept.substring(kept.indexOf('\n') + 1) : typed;
            return username + "\n" + required(password);
        }
        String value = typed.strip().isEmpty() ? kept : typed.strip();
        return required(value);
    }

    private static String required(String secret) {
        if (secret == null || secret.isEmpty()) {
            throw new InvalidIntegration("secret-required", "Paste the secret first.");
        }
        if (secret.length() > MAX_SECRET_LENGTH || secret.chars().anyMatch(c -> c == '\r' || c == '\n' || c == 0)) {
            throw new InvalidIntegration("secret-invalid", "That doesn't look like a key or a token. Copy it again.");
        }
        return secret;
    }

    private static String account(Connection connection) {
        return connection.auth().kind() == ApiAuth.Kind.BASIC && connection.username() != null
                ? connection.username().strip() : null;
    }

    private static String ending(ApiAuth auth, String secret) {
        if (secret == null) {
            return null;
        }
        String part = auth.kind() == ApiAuth.Kind.BASIC ? secret.substring(secret.indexOf('\n') + 1) : secret;
        return Integration.endingOf(part);
    }

    private String icon(URI address) {
        try {
            return gateway.siteIcon(address);
        } catch (RuntimeException unusable) {
            log.debug("No site icon for an API: {}", unusable.getClass().getSimpleName());
            return null;
        }
    }

    private String secretOf(Integration integration) {
        if (!integration.auth().hasSecret()) {
            return null;
        }
        return integrations.secret(integration.workspaceId(), integration.id()).orElseThrow(ManageIntegrations::gone);
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
