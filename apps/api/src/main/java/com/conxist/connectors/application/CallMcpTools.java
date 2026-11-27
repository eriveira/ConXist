package com.conxist.connectors.application;

import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.conxist.connectors.McpToolbox;
import com.conxist.connectors.domain.Integration;
import com.conxist.connectors.domain.McpServer;
import com.conxist.connectors.domain.McpTool;
import com.conxist.connectors.domain.McpToolNames;
import com.conxist.platform.CurrentWorkspace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The current workspace's MCP tools, and its integrations' tools (ADR 0019, ADR 0020), as a run offers and calls them
 * (ADR 0017). A tool is offered and called only while the workspace allows it and the server marks it read-only, or
 * it is one of ConXist's own read tools for an integration, checked again on every call. What a tool answers is
 * untrusted data, cut to {@link #MAX_ANSWER_CHARACTERS}; a run wraps it as data before the model reads it.
 */
public final class CallMcpTools implements McpToolbox {

    private static final Logger log = LoggerFactory.getLogger(CallMcpTools.class);
    static final int MAX_ANSWER_CHARACTERS = 100_000;
    /** An error says what went wrong in a few lines; more is not resent to the model turn after turn. */
    static final int MAX_ERROR_CHARACTERS = 2_000;
    /**
     * What the model may send a server in one call. A lookup needs a few words; a limit keeps meeting text or earlier
     * answers from leaving ConXist in a tool's arguments, whatever a transcript or a tool's description asks.
     */
    static final int MAX_ARGUMENT_CHARACTERS = 4_000;

    private final McpServerRepository servers;
    private final McpCredentials credentials;
    private final McpGateway gateway;
    private final IntegrationRepository integrations;
    private final Map<Integration.Kind, IntegrationApi> apis = new EnumMap<>(Integration.Kind.class);
    private final CurrentWorkspace workspace;
    private final Clock clock;

    public CallMcpTools(McpServerRepository servers, McpCredentials credentials, McpGateway gateway,
            IntegrationRepository integrations, List<IntegrationApi> apis, CurrentWorkspace workspace, Clock clock) {
        this.servers = servers;
        this.credentials = credentials;
        this.gateway = gateway;
        this.integrations = integrations;
        apis.forEach(api -> this.apis.put(api.kind(), api));
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
            if (integration != null && integration.allows(pick.tool())) {
                McpTool tool = integration.tool(pick.tool()).orElseThrow();
                String name = McpToolNames.forModel(integration.name(), tool.name(), taken);
                taken.add(name);
                offered.add(new OfferedTool(pick, name, tool.description(), tool.inputSchema()));
                continue;
            }
            McpServer server = byId.get(pick.serverId());
            if (server == null || !server.allows(pick.tool())) {
                continue;
            }
            McpTool tool = server.tool(pick.tool()).orElseThrow();
            String name = McpToolNames.forModel(server.name(), tool.name(), taken);
            taken.add(name);
            offered.add(new OfferedTool(pick, name, description(server, tool), tool.inputSchema()));
        }
        return List.copyOf(offered);
    }

    @Override
    public Result call(ToolRef ref, Map<String, Object> arguments) {
        Optional<Integration> integration = integrations.find(workspace.id(), ref.serverId());
        if (integration.isPresent()) {
            return callIntegration(integration.get(), ref.tool(), arguments == null ? Map.of() : arguments);
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

    /** One of ConXist's own tools for an integration; its answer is the service's data, untrusted. */
    private Result callIntegration(Integration integration, String tool, Map<String, Object> arguments) {
        if (!integration.allows(tool)) {
            return new Result("This tool is no longer allowed in this workspace.", true);
        }
        if (String.valueOf(arguments).length() > MAX_ARGUMENT_CHARACTERS) {
            return new Result("The arguments are too long. Send this tool only what it asks for.", true);
        }
        if (integration.status() == Integration.Status.SIGN_IN_NEEDED) {
            return new Result(integration.name() + " refused ConXist's token. A person replaces it in Settings.", true);
        }
        IntegrationApi api = apis.get(integration.kind());
        if (api == null) {
            return new Result("This tool is no longer allowed in this workspace.", true);
        }
        return integrations.token(integration.workspaceId(), integration.id())
                .map(token -> callService(api, integration, token, tool, arguments))
                .orElseGet(() -> new Result("This integration is no longer in this workspace.", true));
    }

    private Result callService(IntegrationApi api, Integration integration, String credential, String tool,
            Map<String, Object> arguments) {
        String name = integration.name();
        try {
            return new Result(capped(api.call(integration, credential, tool, arguments), MAX_ANSWER_CHARACTERS),
                    false);
        } catch (IntegrationApi.Problem problem) {
            return new Result(problem.getMessage(), true);
        } catch (IntegrationApi.Missing missing) {
            return new Result(name + " has nothing there, or the token can't see it.", true);
        } catch (IntegrationApi.Refused refused) {
            log.info("Workspace {}: {} refused a call to {} on integration {}", integration.workspaceId(), name, tool,
                    integration.id());
            return new Result(name + " refused this request. It may be limiting requests: try again later.", true);
        } catch (IntegrationApi.TokenRefused refused) {
            integrations.update(integration.withStatus(Integration.Status.SIGN_IN_NEEDED, clock.instant()));
            log.info("Workspace {}: {} refused the token of integration {}", integration.workspaceId(), name,
                    integration.id());
            return new Result(name + " refused ConXist's token. A person replaces it in Settings.", true);
        } catch (IntegrationApi.Unreachable unreachable) {
            log.info("Workspace {}: {} could not be reached for integration {}", integration.workspaceId(), name,
                    integration.id());
            return new Result(name + " could not be reached. Try again later.", true);
        } catch (RuntimeException unexpected) {
            // Only the class: the message may hold what the model searched for, which is customer data.
            log.warn("Workspace {}: a call to {} for integration {} failed: {}", integration.workspaceId(), name,
                    integration.id(), unexpected.getClass().getSimpleName());
            return new Result(name + " could not be reached. Try again later.", true);
        }
    }

    private McpGateway.ToolAnswer callWithRefresh(McpServer server, String tool, Map<String, Object> arguments) {
        try {
            return gateway.call(server.address(), credentials.bearer(server), tool, arguments);
        } catch (McpGateway.SignInRefused refused) {
            return gateway.call(server.address(), credentials.refreshed(server), tool, arguments);
        }
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
