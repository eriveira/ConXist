package com.conxist.connectors.config;

import java.net.http.HttpClient;
import java.time.Clock;
import com.conxist.agents.AgentBriefs;
import com.conxist.agents.AgentMcpTools;
import com.conxist.connectors.McpToolbox;
import com.conxist.connectors.adapter.github.HttpGitHubApi;
import com.conxist.connectors.adapter.mcp.HttpMcpGateway;
import com.conxist.connectors.application.AllowedMcpTools;
import com.conxist.connectors.application.CallMcpTools;
import com.conxist.connectors.application.ConnectMcpServer;
import com.conxist.connectors.application.GitHubApi;
import com.conxist.connectors.application.IntegrationRepository;
import com.conxist.connectors.application.IntegrationViews;
import com.conxist.connectors.application.ManageIntegrations;
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

/** Wires MCP servers and integrations: the HTTP gateways, the connections being made, and the use cases. */
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

    /** GET only, with timeouts and no redirects, so the token reaches only GitHub's API. */
    @Bean
    GitHubApi gitHubApi(ConnectorsProperties properties) {
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
        requests.setReadTimeout(properties.readTimeout());
        return new HttpGitHubApi(RestClient.builder().requestFactory(requests).build(), properties.githubApi());
    }

    @Bean
    IntegrationViews integrationViews(McpServerViews views) {
        return new IntegrationViews(views);
    }

    @Bean
    ManageIntegrations manageIntegrations(IntegrationRepository integrations, GitHubApi github,
            IntegrationViews views, CurrentWorkspace workspace, Clock clock) {
        return new ManageIntegrations(integrations, github, views, workspace, clock);
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
    AgentMcpTools agentMcpTools(McpServerRepository servers, IntegrationRepository integrations,
            CurrentWorkspace workspace) {
        return new AllowedMcpTools(servers, integrations, workspace);
    }

    /** What runs offer and call. */
    @Bean
    McpToolbox mcpToolbox(McpServerRepository servers, McpCredentials credentials, McpGateway gateway,
            IntegrationRepository integrations, GitHubApi github, CurrentWorkspace workspace, Clock clock) {
        return new CallMcpTools(servers, credentials, gateway, integrations, github, workspace, clock);
    }
}
