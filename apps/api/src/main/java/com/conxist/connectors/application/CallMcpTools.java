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
import com.conxist.connectors.domain.McpServer;
import com.conxist.connectors.domain.McpTool;
import com.conxist.connectors.domain.McpToolNames;
import com.conxist.platform.CurrentWorkspace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The current workspace's MCP tools as a run offers and calls them (ADR 0017). A tool is offered and called only while
 * the workspace allows it and the server marks it read-only, checked again on every call. What a tool answers is
 * untrusted data, cut to {@link #MAX_ANSWER_CHARACTERS}; a run wraps it as data before the model reads it.
 */
public final class CallMcpTools implements McpToolbox {

    private static final Logger log = LoggerFactory.getLogger(CallMcpTools.class);
    static final int MAX_ANSWER_CHARACTERS = 100_000;

    private final McpServerRepository servers;
    private final McpCredentials credentials;
    private final McpGateway gateway;
    private final CurrentWorkspace workspace;
    private final Clock clock;

    public CallMcpTools(McpServerRepository servers, McpCredentials credentials, McpGateway gateway,
            CurrentWorkspace workspace, Clock clock) {
        this.servers = servers;
        this.credentials = credentials;
        this.gateway = gateway;
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
        Set<String> taken = new HashSet<>();
        List<OfferedTool> offered = new ArrayList<>();
        for (ToolRef pick : picks) {
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
        Optional<McpServer> found = servers.find(workspace.id(), ref.serverId());
        if (found.isEmpty() || !found.get().allows(ref.tool())) {
            return new Result("This tool is no longer allowed in this workspace.", true);
        }
        McpServer server = found.get();
        try {
            McpGateway.ToolAnswer answer = callWithRefresh(server, ref.tool(), arguments == null ? Map.of() : arguments);
            return new Result(capped(answer.text()), answer.error());
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

    private static String capped(String text) {
        String value = text == null ? "" : text;
        return value.length() <= MAX_ANSWER_CHARACTERS ? value
                : value.substring(0, MAX_ANSWER_CHARACTERS) + "\n(The answer was cut here.)";
    }
}
