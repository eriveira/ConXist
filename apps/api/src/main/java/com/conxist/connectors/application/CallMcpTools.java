package com.conxist.connectors.application;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.conxist.connectors.McpToolbox;
import com.conxist.connectors.domain.ApiTool;
import com.conxist.connectors.domain.Integration;
import com.conxist.connectors.domain.McpServer;
import com.conxist.connectors.domain.McpTool;
import com.conxist.connectors.domain.McpToolNames;
import com.conxist.platform.CurrentWorkspace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The current workspace's MCP tools, and its APIs' tools (ADR 0027), as a run offers and calls them (ADR 0017). A tool
 * is offered and called only while the workspace allows it, checked again on every call. What a tool answers is
 * untrusted data, cut to {@link #MAX_ANSWER_CHARACTERS}; a run wraps it as data before the model reads it.
 */
public final class CallMcpTools implements McpToolbox {

    private static final Logger log = LoggerFactory.getLogger(CallMcpTools.class);
    static final int MAX_ANSWER_CHARACTERS = 100_000;
    /** What a custom API's answer may hold for the model (ADR 0027): enough for a page of results. */
    static final int MAX_API_ANSWER_CHARACTERS = 20_000;
    /** An error says what went wrong in a few lines; more is not resent to the model turn after turn. */
    static final int MAX_ERROR_CHARACTERS = 2_000;
    /** What the model may send a server in one call (the toolbox's own limit, which a run also checks first). */
    static final int MAX_ARGUMENT_CHARACTERS = McpToolbox.MAX_ARGUMENT_CHARACTERS;

    private final McpServerRepository servers;
    private final McpCredentials credentials;
    private final McpGateway gateway;
    private final IntegrationRepository integrations;
    private final ApiGateway api;
    private final CurrentWorkspace workspace;
    private final Clock clock;

    public CallMcpTools(McpServerRepository servers, McpCredentials credentials, McpGateway gateway,
            IntegrationRepository integrations, ApiGateway api, CurrentWorkspace workspace, Clock clock) {
        this.servers = servers;
        this.credentials = credentials;
        this.gateway = gateway;
        this.integrations = integrations;
        this.api = api;
        this.workspace = workspace;
        this.clock = clock;
    }

    @Override
    public List<OfferedTool> offer(List<ToolRef> picks) {
        if (picks.isEmpty()) {
            return List.of();
        }
        Map<UUID, McpServer> byId = servers.findAll(workspace.id()).stream()
                .collect(Collectors.toMap(McpServer::id, Function.identity()));
        Map<UUID, Integration> integrationsById = integrations.findAll(workspace.id()).stream()
                .collect(Collectors.toMap(Integration::id, Function.identity()));
        Set<String> taken = new HashSet<>();
        List<OfferedTool> offered = new ArrayList<>();
        for (ToolRef pick : picks) {
            Integration integration = integrationsById.get(pick.serverId());
            if (integration != null) {
                integration.tool(pick.tool()).filter(ApiTool::allowed).ifPresent(tool -> {
                    String name = McpToolNames.forModel(integration.name(), tool.name(), taken);
                    taken.add(name);
                    offered.add(new OfferedTool(pick, name, description(integration, tool), tool.inputSchema(),
                            !tool.reads()));
                });
                continue;
            }
            McpServer server = byId.get(pick.serverId());
            if (server == null || !server.allows(pick.tool())) {
                continue;
            }
            McpTool tool = server.tool(pick.tool()).orElseThrow();
            String name = McpToolNames.forModel(server.name(), tool.name(), taken);
            taken.add(name);
            offered.add(new OfferedTool(pick, name, description(server, tool), tool.inputSchema(), !tool.readOnly()));
        }
        return List.copyOf(offered);
    }

    @Override
    public Result call(ToolRef ref, Map<String, Object> arguments) {
        Optional<Integration> integration = integrations.find(workspace.id(), ref.serverId());
        if (integration.isPresent()) {
            return callApi(integration.get(), ref.tool(), arguments == null ? Map.of() : arguments);
        }
        Optional<McpServer> found = servers.find(workspace.id(), ref.serverId());
        if (found.isEmpty() || !found.get().allows(ref.tool())) {
            return new Result("This tool is no longer allowed in this workspace.", true);
        }
        McpServer server = found.get();
        Map<String, Object> sent = arguments == null ? Map.of() : arguments;
        if (String.valueOf(sent).length() > MAX_ARGUMENT_CHARACTERS) {
            log.info("Workspace {}: a call to {} on MCP server {} was refused, its arguments are too long",
                    server.workspaceId(), ref.tool(), server.id());
            return new Result("The arguments are too long. Send this tool only the few words it needs to look"
                    + " something up.", true);
        }
        try {
            McpGateway.ToolAnswer answer = callWithRefresh(server, ref.tool(), sent);
            return new Result(capped(answer.text(), answer.error() ? MAX_ERROR_CHARACTERS : MAX_ANSWER_CHARACTERS),
                    answer.error());
        } catch (McpGateway.SignInRefused refused) {
            servers.update(server.withStatus(McpServer.Status.SIGN_IN_NEEDED, clock.instant()));
            log.info("Workspace {}: MCP server {} refused ConXist's sign-in", server.workspaceId(), server.id());
            return new Result("The server refused ConXist's sign-in. A person signs in again in Settings.", true);
        } catch (McpGateway.Unreachable | McpGateway.NotMcp | McpGateway.AddressRefused unreachable) {
            log.info("Workspace {}: MCP server {} could not be reached for a tool call", server.workspaceId(),
                    server.id());
            return new Result("The server could not be reached. Try again later.", true);
        }
    }

    /**
     * One tool of a custom API (ADR 0027): the request goes only to the API's address, with the sign-in ConXist adds;
     * the answer is the API's data, untrusted. A refused sign-in marks the API so a person replaces the secret.
     */
    private Result callApi(Integration integration, String toolName, Map<String, Object> arguments) {
        Optional<ApiTool> tool = integration.tool(toolName).filter(ApiTool::allowed);
        if (tool.isEmpty()) {
            return new Result("This tool is no longer allowed in this workspace.", true);
        }
        if (String.valueOf(arguments).length() > MAX_ARGUMENT_CHARACTERS) {
            return new Result("The arguments are too long. Send this tool only what it asks for.", true);
        }
        String name = integration.name();
        if (integration.status() == Integration.Status.SIGN_IN_NEEDED) {
            return new Result(name + " refused ConXist's sign-in. A person replaces it in Settings.", true);
        }
        String secret = null;
        if (integration.auth().hasSecret()) {
            Optional<String> stored = integrations.secret(integration.workspaceId(), integration.id());
            if (stored.isEmpty()) {
                return new Result("This API is no longer in this workspace.", true);
            }
            secret = stored.get();
        }
        ApiGateway.Request request;
        try {
            request = ApiRequests.build(integration, secret, tool.get(), arguments);
        } catch (ApiRequests.Problem problem) {
            return new Result(problem.getMessage(), true);
        }
        try {
            ApiGateway.Answer answer = api.send(request);
            return answered(integration, toolName, answer);
        } catch (ApiGateway.AddressRefused | ApiGateway.Unreachable unreachable) {
            log.info("Workspace {}: API {} could not be reached for a tool call", integration.workspaceId(),
                    integration.id());
            return new Result(name + " could not be reached. Try again later.", true);
        } catch (RuntimeException unexpected) {
            // Only the class: the message may hold what the model sent, which is customer data.
            log.warn("Workspace {}: a call to API {} failed: {}", integration.workspaceId(), integration.id(),
                    unexpected.getClass().getSimpleName());
            return new Result(name + " could not be reached. Try again later.", true);
        }
    }

    private Result answered(Integration integration, String toolName, ApiGateway.Answer answer) {
        String name = integration.name();
        int status = answer.status();
        if (status == 401) {
            integrations.update(integration.withStatus(Integration.Status.SIGN_IN_NEEDED, clock.instant()));
            log.info("Workspace {}: API {} refused ConXist's sign-in", integration.workspaceId(), integration.id());
            return new Result(name + " refused ConXist's sign-in. A person replaces it in Settings.", true);
        }
        if (status == 429) {
            return new Result(name + " is limiting requests. Try again later.", true);
        }
        String body = answer.text() == null
                ? name + " answered with " + (answer.contentType().isEmpty() ? "something" : answer.contentType())
                        + " (" + answer.bytes() + " bytes), which ConXist doesn't read."
                : answer.text();
        if (answer.ok()) {
            return new Result(capped("HTTP " + status + "\n" + body, MAX_API_ANSWER_CHARACTERS), false);
        }
        log.info("Workspace {}: tool {} of API {} answered HTTP {}", integration.workspaceId(), toolName,
                integration.id(), status);
        return new Result(capped("HTTP " + status + "\n" + body, MAX_ERROR_CHARACTERS), true);
    }

    private McpGateway.ToolAnswer callWithRefresh(McpServer server, String tool, Map<String, Object> arguments) {
        try {
            return gateway.call(server.address(), credentials.bearer(server), tool, arguments);
        } catch (McpGateway.SignInRefused refused) {
            return gateway.call(server.address(), credentials.refreshed(server), tool, arguments);
        }
    }

    /** The API's own words about the tool, or a person's: untrusted, so the run's prompt treats them as data. */
    private static String description(Integration integration, ApiTool tool) {
        return "A tool of the API \"" + integration.name() + "\" (" + tool.name() + ", " + tool.method() + " "
                + tool.path() + "). " + tool.description();
    }

    /** The server's own words about the tool: untrusted, so the run's prompt treats tool descriptions as data. */
    private static String description(McpServer server, McpTool tool) {
        String about = tool.description().isEmpty() ? tool.title() : tool.description();
        return "A tool of the MCP server \"" + server.name() + "\" (" + tool.name() + "). " + about;
    }

    private static String capped(String text, int max) {
        String value = text == null ? "" : text;
        return value.length() <= max ? value : value.substring(0, max) + "\n(The answer was cut here.)";
    }
}
