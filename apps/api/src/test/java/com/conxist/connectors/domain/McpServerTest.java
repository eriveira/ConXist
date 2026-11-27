package com.conxist.connectors.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.conxist.platform.RuleViolation;
import org.junit.jupiter.api.Test;

class McpServerTest {

    private static final Instant NOW = Instant.parse("2026-11-27T10:00:00Z");

    @Test
    void a_tool_that_changes_things_starts_off_and_can_be_allowed_like_any_other() {
        McpServer server = server(Set.of("search"));

        assertThat(server.allows("search")).isTrue();
        assertThat(server.allows("create")).isFalse();
        assertThat(server.allow(Set.of("search", "create")).allows("create")).isTrue();
        assertCode(() -> server.allow(Set.of("gone")), "mcp.tool-unknown");
    }

    @Test
    void a_new_list_keeps_an_allowed_tool_that_changes_things() {
        McpServer server = server(Set.of("search")).allow(Set.of("search", "create"));

        McpServer listed = server.listed(List.of(tool("search", true), tool("create", false)), null, NOW);

        assertThat(listed.allows("create")).isTrue();
    }

    @Test
    void a_tool_that_changes_things_loses_its_tick_when_it_says_or_takes_something_else() {
        McpServer server = server(Set.of("search")).allow(Set.of("search", "create"));
        McpTool described = new McpTool("create", "create", "Deletes everything", false, Map.of("type", "object"), false);
        McpTool reshaped = new McpTool("create", "create", "About create", false,
                Map.of("type", "object", "properties", Map.of("all", Map.of("type", "boolean"))), false);

        assertThat(server.listed(List.of(tool("search", true), described), null, NOW).allows("create")).isFalse();
        assertThat(server.listed(List.of(tool("search", true), reshaped), null, NOW).allows("create")).isFalse();
        assertThat(server.listed(List.of(tool("search", true), tool("create", false)), null, NOW).allows("create"))
                .isTrue();
    }

    @Test
    void a_new_list_keeps_ticks_starts_new_tools_off_and_drops_a_tick_on_a_tool_that_now_changes_things() {
        McpServer server = server(Set.of("search", "get"));
        List<McpTool> fresh = List.of(tool("search", true), tool("get", false), tool("list", true));

        McpServer listed = server.listed(fresh, null, NOW.plusSeconds(60));

        assertThat(server.newIn(fresh)).containsExactly("list");
        assertThat(listed.allows("search")).isTrue();
        assertThat(listed.allows("get")).isFalse();
        assertThat(listed.allows("list")).isFalse();
        assertThat(listed.icon()).isEqualTo("data:image/png;base64,AA==");
        assertThat(listed.tool("create")).isEmpty();
    }

    @Test
    void names_are_required_and_short_and_tool_text_is_cut() {
        assertCode(() -> server(Set.of()).renamed("  "), "mcp.name-required");
        assertCode(() -> server(Set.of()).renamed("x".repeat(61)), "mcp.name-too-long");
        McpTool long_ = new McpTool("t", "T", "d".repeat(5_000), true, null, false);
        assertThat(long_.description()).hasSize(McpTool.MAX_DESCRIPTION_LENGTH).endsWith("…");
        assertThat(long_.inputSchema()).isEqualTo(Map.of("type", "object"));
        assertCode(() -> new McpTool("has space", "", "", true, null, false), "mcp.tool-name");
    }

    @Test
    void model_names_use_only_safe_characters_fit_64_and_never_repeat() {
        Set<String> taken = new HashSet<>();
        String first = McpToolNames.forModel("Acme Docs!", "search.docs/v2", taken);
        taken.add(first);
        String second = McpToolNames.forModel("acme docs", "search-docs/v2", taken);
        String longOne = McpToolNames.forModel("x".repeat(80), "y".repeat(128), Set.of());

        assertThat(first).isEqualTo("acme_docs__search_docs_v2");
        assertThat(second).isEqualTo("acme_docs__search-docs_v2");
        assertThat(McpToolNames.forModel("Acme Docs", "search.docs/v2", taken)).isEqualTo("acme_docs__search_docs_v2_2");
        assertThat(longOne).hasSize(64).matches("[A-Za-z0-9_-]+");
    }

    private static McpServer server(Set<String> allowed) {
        return McpServer.add(UUID.randomUUID(), UUID.randomUUID(), "Acme", URI.create("https://mcp.acme.test/mcp"),
                McpAuth.NONE, "data:image/png;base64,AA==",
                List.of(tool("search", true), tool("get", true), tool("create", false)), allowed, NOW);
    }

    private static McpTool tool(String name, boolean readOnly) {
        return new McpTool(name, name, "About " + name, readOnly, Map.of("type", "object"), false);
    }

    private static void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOf(RuleViolation.class)
                .extracting(e -> ((RuleViolation) e).code()).isEqualTo(code);
    }
}
