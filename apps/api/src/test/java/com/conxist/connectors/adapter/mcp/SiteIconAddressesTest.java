package com.conxist.connectors.adapter.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;

class SiteIconAddressesTest {

    @Test
    void a_server_on_a_subdomain_tries_its_own_host_then_the_parent_domain() {
        assertThat(HttpMcpGateway.siteIconAddresses(URI.create("https://mcp.atlassian.com/v1/sse")))
                .extracting(URI::toString)
                .containsExactly("https://mcp.atlassian.com/apple-touch-icon.png", "https://mcp.atlassian.com/favicon.ico",
                        "https://atlassian.com/apple-touch-icon.png", "https://atlassian.com/favicon.ico");
    }

    @Test
    void a_server_on_a_bare_domain_or_an_address_tries_only_its_own_host_and_keeps_its_port() {
        assertThat(HttpMcpGateway.siteIconAddresses(URI.create("https://acme.com/mcp")))
                .extracting(URI::toString)
                .containsExactly("https://acme.com/apple-touch-icon.png", "https://acme.com/favicon.ico");
        assertThat(HttpMcpGateway.siteIconAddresses(URI.create("http://127.0.0.1:8123/mcp")))
                .extracting(URI::toString)
                .containsExactly("http://127.0.0.1:8123/apple-touch-icon.png", "http://127.0.0.1:8123/favicon.ico");
    }
}
