package com.conxist.connectors.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import com.conxist.connectors.McpToolbox;
import com.conxist.connectors.domain.McpAuth;
import com.conxist.connectors.domain.McpServer;
import com.conxist.connectors.domain.McpTool;
import org.junit.jupiter.api.Test;

class CallMcpToolsTest {

    private static final UUID WORKSPACE = UUID.randomUUID();
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-27T10:00:00Z"), ZoneOffset.UTC);

    private final Map<UUID, McpServer> stored = new HashMap<>();
    private final List<Map<String, Object>> sent = new ArrayList<>();
    private String answer = "found it";
    private boolean failing;
    private final McpGateway gateway = new McpGateway() {
        public URI checked(String address) { throw new UnsupportedOperationException(); }
        public String image(URI address) { throw new UnsupportedOperationException(); }
        public String siteIcon(URI address) { throw new UnsupportedOperationException(); }
        public ServerFacts list(URI address, String bearer) { throw new UnsupportedOperationException(); }
        public Optional<SignIn> signInFor(URI address) { throw new UnsupportedOperationException(); }
        public URI authorizationUrl(SignIn signIn, String state, String verifier) { throw new UnsupportedOperationException(); }
        public Tokens exchange(SignIn signIn, String code, String verifier) { throw new UnsupportedOperationException(); }
        public Tokens refresh(SignIn signIn, String refreshToken) { throw new UnsupportedOperationException(); }
        public ToolAnswer call(URI address, String bearer, String tool, Map<String, Object> arguments) {
            sent.add(arguments);
            return new ToolAnswer(answer, failing);
        }
    };
    private final McpServerRepository servers = new McpServerRepository() {
        public List<McpServer> findAll(UUID workspaceId) { return List.copyOf(stored.values()); }
        public Optional<McpServer> find(UUID workspaceId, UUID serverId) {
            return Optional.ofNullable(stored.get(serverId)).filter(server -> server.workspaceId().equals(workspaceId));
        }
        public boolean insertIfNameFree(McpServer server, McpCredential credential) { stored.put(server.id(), server); return true; }
        public boolean update(McpServer server) { stored.put(server.id(), server); return true; }
        public boolean saveLogo(UUID workspaceId, UUID serverId, McpServer.Logo logo) { return false; }
        public Optional<McpCredential> credential(UUID workspaceId, UUID serverId) { return Optional.of(McpCredential.none()); }
        public void saveCredential(UUID workspaceId, UUID serverId, McpCredential credential) { }
        public boolean delete(UUID workspaceId, UUID serverId) { return stored.remove(serverId) != null; }
    };
    /** No APIs here: their tools are tested in CallApiToolsTest and IntegrationsIntegrationTest. */
    private final IntegrationRepository noIntegrations = new IntegrationRepository() {
        public List<com.conxist.connectors.domain.Integration> findAll(UUID workspaceId) { return List.of(); }
        public Optional<com.conxist.connectors.domain.Integration> find(UUID workspaceId, UUID id) { return Optional.empty(); }
        public boolean insert(com.conxist.connectors.domain.Integration integration, String secret) { return false; }
        public void update(com.conxist.connectors.domain.Integration integration) { }
        public boolean updateConnection(com.conxist.connectors.domain.Integration integration, String secret) { return false; }
        public Optional<String> secret(UUID workspaceId, UUID id) { return Optional.empty(); }
        public boolean delete(UUID workspaceId, UUID id) { return false; }
    };
    private final CallMcpTools toolbox = new CallMcpTools(servers, new McpCredentials(servers, gateway, CLOCK), gateway,
            noIntegrations, null, () -> WORKSPACE, CLOCK);

    @Test
    void a_tool_unticked_after_it_was_offered_is_not_called() {
        McpServer server = added();
        McpToolbox.ToolRef ref = new McpToolbox.ToolRef(server.id(), "search_docs");
        assertThat(toolbox.offer(List.of(ref))).hasSize(1);

        stored.put(server.id(), server.allow(Set.of()));

        assertThat(toolbox.call(ref, Map.of("query", "checkout")).error()).isTrue();
        assertThat(sent).isEmpty();
    }

    @Test
    void long_arguments_never_leave_conxist() {
        McpToolbox.ToolRef ref = new McpToolbox.ToolRef(added().id(), "search_docs");

        McpToolbox.Result refused = toolbox.call(ref, Map.of("query", "x".repeat(CallMcpTools.MAX_ARGUMENT_CHARACTERS)));

        assertThat(refused.error()).isTrue();
        assertThat(refused.text()).contains("too long");
        assertThat(sent).isEmpty();
        assertThat(toolbox.call(ref, Map.of("query", "checkout")).error()).isFalse();
        assertThat(sent).containsExactly(Map.of("query", "checkout"));
    }

    @Test
    void an_answer_is_cut_and_an_error_is_cut_shorter() {
        McpToolbox.ToolRef ref = new McpToolbox.ToolRef(added().id(), "search_docs");
        answer = "y".repeat(CallMcpTools.MAX_ANSWER_CHARACTERS + 10);

        assertThat(toolbox.call(ref, Map.of()).text()).hasSize(CallMcpTools.MAX_ANSWER_CHARACTERS
                + "\n(The answer was cut here.)".length());
        failing = true;
        assertThat(toolbox.call(ref, Map.of()).text()).hasSize(CallMcpTools.MAX_ERROR_CHARACTERS
                + "\n(The answer was cut here.)".length());
    }

    private McpServer added() {
        McpTool search = new McpTool("search_docs", "Search", "Finds docs.", true,
                Map.of("type", "object", "properties", Map.of("query", Map.of("type", "string"))), false);
        McpServer server = McpServer.add(UUID.randomUUID(), WORKSPACE, "Acme Docs", URI.create("https://mcp.acme.test/mcp"),
                McpAuth.NONE, null, List.of(search), Set.of("search_docs"), CLOCK.instant());
        stored.put(server.id(), server);
        return server;
    }
}
