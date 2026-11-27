package com.conxist.connectors.adapter.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;

class SiteIconTest {

    @Test
    void a_server_on_a_subdomain_looks_on_its_own_site_then_the_parent_domain() {
        assertThat(HttpMcpGateway.siteHomes(URI.create("https://mcp.cloudflare.com/mcp"))).extracting(URI::toString)
                .containsExactly("https://mcp.cloudflare.com/", "https://cloudflare.com/");
    }

    @Test
    void a_bare_domain_or_an_address_looks_only_on_itself_and_keeps_its_port() {
        assertThat(HttpMcpGateway.siteHomes(URI.create("https://acme.com/mcp"))).extracting(URI::toString)
                .containsExactly("https://acme.com/");
        assertThat(HttpMcpGateway.siteHomes(URI.create("http://127.0.0.1:8123/mcp"))).extracting(URI::toString)
                .containsExactly("http://127.0.0.1:8123/");
    }

    @Test
    void a_page_gives_its_linked_icons_touch_icons_first_resolved_against_where_it_ended_up() {
        String html = """
                <html><head>
                <link rel="stylesheet" href="/a.css">
                <link rel="icon" type="image/png" href="/static/favicon-32.png">
                <LINK REL='apple-touch-icon' HREF='https://cdn.plaud.ai/touch.png?v=1&amp;x=2'>
                <link rel="mask-icon" href="/mask.svg">
                <link href="icons/shortcut.ico" rel="shortcut icon">
                </head></html>""";

        assertThat(HttpMcpGateway.linkedIcons(html, URI.create("https://www.plaud.ai/en/"))).extracting(URI::toString)
                .containsExactly("https://cdn.plaud.ai/touch.png?v=1&x=2", "https://www.plaud.ai/static/favicon-32.png",
                        "https://www.plaud.ai/en/icons/shortcut.ico");
    }
}
