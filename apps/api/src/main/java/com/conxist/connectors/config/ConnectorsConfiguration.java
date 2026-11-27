package com.conxist.connectors.config;

import java.net.http.HttpClient;
import java.time.Clock;
import com.conxist.agents.AgentBriefs;
import com.conxist.agents.AgentMcpTools;
import com.conxist.connectors.McpToolbox;
import com.conxist.connectors.adapter.mcp.HttpMcpGateway;
import com.conxist.connectors.application.AllowedMcpTools;
import com.conxist.connectors.application.CallMcpTools;
import com.conxist.connectors.application.ConnectMcpServer;
import com.conxist.connectors.application.ManageMcpServers;
import com.conxist.connectors.application.McpConnections;
import com.conxist.connectors.application.McpCredentials;
import com.conxist.connectors.application.McpGateway;
import com.conxist.connectors.application.McpServerRepository;
import com.conxist.connectors.application.McpServerViews;
import com.conxist.platform.CurrentWorkspace;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Wires MCP servers: the HTTP gateway, the connections being made, and the use cases. */
@Configuration(proxyBeanMethods = false)
class ConnectorsConfiguration {

    /** Timeouts, and no redirects, so a token is never sent to another host. */
    @Bean
    McpGateway mcpGateway(ConnectorsProperties properties, Clock clock) {
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
        requests.setReadTimeout(properties.readTimeout());
        return new HttpMcpGateway(RestClient.builder().requestFactory(requests).build(), properties.allowLoopback(),
                properties.redirectUri(), clock);
    }

    @Bean
    McpConnections mcpConnections(Clock clock) {
        return new McpConnections(clock);
    }

    @Bean
    McpCredentials mcpCredentials(McpServerRepository servers, McpGateway gateway, Clock clock) {
        return new McpCredentials(servers, gateway, clock);
    }

    @Bean
    McpServerViews mcpServerViews(AgentBriefs agents) {
        return new McpServerViews(agents);
    }

    @Bean
    ConnectMcpServer connectMcpServer(McpGateway gateway, McpConnections connections, McpServerRepository servers,
            McpServerViews views, CurrentWorkspace workspace) {
        return new ConnectMcpServer(gateway, connections, servers, views, workspace);
    }

    @Bean
    ManageMcpServers manageMcpServers(McpServerRepository servers, McpConnections connections,
            McpCredentials credentials, McpGateway gateway, McpServerViews views, CurrentWorkspace workspace,
            Clock clock) {
        return new ManageMcpServers(servers, connections, credentials, gateway, views, workspace, clock);
    }

    /** Answers the agents module whether a tool is still allowed. */
    @Bean
    AgentMcpTools agentMcpTools(McpServerRepository servers, CurrentWorkspace workspace) {
        return new AllowedMcpTools(servers, workspace);
    }

    /** What runs offer and call. */
    @Bean
    McpToolbox mcpToolbox(McpServerRepository servers, McpCredentials credentials, McpGateway gateway,
            CurrentWorkspace workspace, Clock clock) {
        return new CallMcpTools(servers, credentials, gateway, workspace, clock);
    }
}
