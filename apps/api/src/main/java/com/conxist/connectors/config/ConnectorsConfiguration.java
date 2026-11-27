package com.conxist.connectors.config;

import java.net.http.HttpClient;
import java.time.Clock;
import java.util.List;
import com.conxist.agents.AgentBriefs;
import com.conxist.agents.AgentMcpTools;
import com.conxist.connectors.McpToolbox;
import com.conxist.connectors.adapter.bitbucket.HttpBitbucketApi;
import com.conxist.connectors.adapter.github.HttpGitHubApi;
import com.conxist.connectors.adapter.gmail.GoogleOAuth;
import com.conxist.connectors.adapter.gmail.HttpGmailApi;
import com.conxist.connectors.adapter.mcp.HttpMcpGateway;
import com.conxist.connectors.application.AllowedMcpTools;
import com.conxist.connectors.application.CallMcpTools;
import com.conxist.connectors.application.ConnectGmail;
import com.conxist.connectors.application.ConnectMcpServer;
import com.conxist.connectors.application.GmailSignIns;
import com.conxist.connectors.application.IntegrationApi;
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
                properties.faviconService(), properties.redirectUri(), clock);
    }

    /** GET only, with timeouts and no redirects, so the token reaches only GitHub's API. */
    @Bean
    IntegrationApi gitHubApi(ConnectorsProperties properties) {
        return new HttpGitHubApi(apiClient(properties), properties.githubApi());
    }

    /** GET only, with timeouts; redirects are followed by hand and only on Bitbucket's own API address. */
    @Bean
    IntegrationApi bitbucketApi(ConnectorsProperties properties) {
        return new HttpBitbucketApi(apiClient(properties), properties.bitbucketApi());
    }

    @Bean
    GoogleOAuth googleOAuth(ConnectorsProperties properties, Clock clock) {
        ConnectorsProperties.Google google = properties.google();
        return new GoogleOAuth(apiClient(properties), new GoogleOAuth.Client(google.clientId(), google.clientSecret(),
                google.authorizationUri(), google.tokenUri(), google.redirectUri()), clock);
    }

    /** GET only, with timeouts and no redirects; access tokens come from the person's Google sign-in. */
    @Bean
    IntegrationApi gmailApi(ConnectorsProperties properties, GoogleOAuth google, Clock clock) {
        return new HttpGmailApi(apiClient(properties), properties.gmailApi(), google, clock);
    }

    @Bean
    GmailSignIns gmailSignIns(Clock clock) {
        return new GmailSignIns(clock);
    }

    @Bean
    ConnectGmail connectGmail(GoogleOAuth google, List<IntegrationApi> apis, GmailSignIns signIns,
            IntegrationRepository integrations, ManageIntegrations manage, CurrentWorkspace workspace) {
        return new ConnectGmail(google, apis, signIns, integrations, manage, workspace);
    }

    private static RestClient apiClient(ConnectorsProperties properties) {
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
        requests.setReadTimeout(properties.readTimeout());
        return RestClient.builder().requestFactory(requests).build();
    }

    @Bean
    IntegrationViews integrationViews(McpServerViews views) {
        return new IntegrationViews(views);
    }

    @Bean
    ManageIntegrations manageIntegrations(IntegrationRepository integrations, List<IntegrationApi> apis,
            IntegrationViews views, CurrentWorkspace workspace, Clock clock) {
        return new ManageIntegrations(integrations, apis, views, workspace, clock);
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
            IntegrationRepository integrations, List<IntegrationApi> apis, CurrentWorkspace workspace, Clock clock) {
        return new CallMcpTools(servers, credentials, gateway, integrations, apis, workspace, clock);
    }
}
