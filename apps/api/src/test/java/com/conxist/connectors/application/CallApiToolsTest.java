package com.conxist.connectors.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import com.conxist.connectors.McpToolbox;
import com.conxist.connectors.domain.ApiAuth;
import com.conxist.connectors.domain.ApiParam;
import com.conxist.connectors.domain.ApiTool;
import com.conxist.connectors.domain.Integration;
import com.conxist.connectors.domain.McpServer;
import org.junit.jupiter.api.Test;

/** An agent's calls to a custom API's tools (ADR 0027), through the same toolbox as MCP tools. */
class CallApiToolsTest {

    private static final UUID WORKSPACE = UUID.randomUUID();
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-28T10:00:00Z"), ZoneOffset.UTC);
    private static final ApiTool SEARCH = new ApiTool("search_deals", "Deals", ApiTool.Method.POST, "/deals/search",
            "Finds deals.", List.of(ApiParam.simple("company", ApiParam.In.BODY, "string", false, "")), true, true,
            true);
    private static final ApiTool DELETE = new ApiTool("delete_contact", "Contacts", ApiTool.Method.DELETE,
            "/contacts/{id}", "Deletes a contact.", List.of(ApiParam.simple("id", ApiParam.In.PATH, "string", true, "")),
            false, true, true);
    private static final ApiTool UNTICKED = new ApiTool("get_me", "Account", ApiTool.Method.GET, "/me", "Who.",
            List.of(), true, true, false);

    private final Map<UUID, Integration> stored = new HashMap<>();
    private final List<ApiGateway.Request> sent = new ArrayList<>();
    private ApiGateway.Answer answer = new ApiGateway.Answer(200, "application/json", "{\"deals\":[]}", 12,
            Duration.ZERO);
    private final ApiGateway api = new ApiGateway() {
        public URI checked(String address) { throw new UnsupportedOperationException(); }
        public Answer send(Request request) {
            sent.add(request);
            return answer;
        }
        public String description(URI address) { throw new UnsupportedOperationException(); }
        public String siteIcon(URI address) { throw new UnsupportedOperationException(); }
        public String image(URI address) { throw new UnsupportedOperationException(); }
    };
    private final IntegrationRepository integrations = new IntegrationRepository() {
        public List<Integration> findAll(UUID workspaceId) { return List.copyOf(stored.values()); }
        public Optional<Integration> find(UUID workspaceId, UUID id) {
            return Optional.ofNullable(stored.get(id)).filter(found -> found.workspaceId().equals(workspaceId));
        }
        public boolean insert(Integration integration, String secret) { stored.put(integration.id(), integration); return true; }
        public void update(Integration integration) { stored.put(integration.id(), integration); }
        public boolean updateConnection(Integration integration, String secret) { return true; }
        public Optional<String> secret(UUID workspaceId, UUID id) { return Optional.of("nw_key_a9F2"); }
        public boolean delete(UUID workspaceId, UUID id) { return stored.remove(id) != null; }
    };
    private final McpServerRepository noServers = new McpServerRepository() {
        public List<McpServer> findAll(UUID workspaceId) { return List.of(); }
        public Optional<McpServer> find(UUID workspaceId, UUID serverId) { return Optional.empty(); }
        public boolean insertIfNameFree(McpServer server, McpCredential credential) { return false; }
        public boolean update(McpServer server) { return false; }
        public boolean saveLogo(UUID workspaceId, UUID serverId, McpServer.Logo logo) { return false; }
        public Optional<McpCredential> credential(UUID workspaceId, UUID serverId) { return Optional.empty(); }
        public void saveCredential(UUID workspaceId, UUID serverId, McpCredential credential) { }
        public boolean delete(UUID workspaceId, UUID serverId) { return false; }
    };
    private final CallMcpTools toolbox = new CallMcpTools(noServers, new McpCredentials(noServers, null, CLOCK), null,
            integrations, api, () -> WORKSPACE, CLOCK);

    @Test
    void only_ticked_tools_are_offered_and_a_change_is_marked_as_one() {
        Integration northwind = added();

        List<McpToolbox.OfferedTool> offered = toolbox.offer(List.of(ref(northwind, "search_deals"),
                ref(northwind, "delete_contact"), ref(northwind, "get_me")));

        assertThat(offered).extracting(McpToolbox.OfferedTool::modelName)
                .containsExactly("northwind__search_deals", "northwind__delete_contact");
        assertThat(offered).extracting(McpToolbox.OfferedTool::changesThings).containsExactly(false, true);
        assertThat(offered.getFirst().description()).startsWith("A tool of the API \"Northwind\" (search_deals, POST");
    }

    @Test
    void a_call_goes_to_the_apis_address_with_the_key_and_the_answer_is_data() {
        Integration northwind = added();

        McpToolbox.Result result = toolbox.call(ref(northwind, "search_deals"), Map.of("company", "Globex"));

        assertThat(result.error()).isFalse();
        assertThat(result.text()).isEqualTo("HTTP 200\n{\"deals\":[]}");
        assertThat(sent).singleElement().satisfies(request -> {
            assertThat(request.url().toString()).isEqualTo("https://api.northwind.test/v1/deals/search");
            assertThat(request.headers()).contains(Map.entry("X-Api-Key", "nw_key_a9F2"));
            assertThat(request.body()).isEqualTo(Map.of("company", "Globex"));
        });
    }

    @Test
    void unticked_too_long_or_wrong_calls_are_never_sent() {
        Integration northwind = added();

        assertThat(toolbox.call(ref(northwind, "get_me"), Map.of()).error()).isTrue();
        assertThat(toolbox.call(ref(northwind, "search_deals"),
                Map.of("company", "x".repeat(CallMcpTools.MAX_ARGUMENT_CHARACTERS))).text()).contains("too long");
        assertThat(toolbox.call(ref(northwind, "search_deals"), Map.of("url", "https://evil.test")).text())
                .contains("isn't an input");
        assertThat(toolbox.call(ref(northwind, "delete_contact"), Map.of()).text()).contains("needs id");
        assertThat(sent).isEmpty();
    }

    @Test
    void a_refused_key_marks_the_api_and_stops_later_calls() {
        Integration northwind = added();
        answer = new ApiGateway.Answer(401, "application/json", "{\"error\":\"bad key\"}", 20, Duration.ZERO);

        assertThat(toolbox.call(ref(northwind, "search_deals"), Map.of()).text()).contains("refused");
        assertThat(stored.get(northwind.id()).status()).isEqualTo(Integration.Status.SIGN_IN_NEEDED);
        assertThat(toolbox.call(ref(northwind, "search_deals"), Map.of()).error()).isTrue();
        assertThat(sent).hasSize(1);
    }

    @Test
    void an_error_answer_is_cut_short_and_a_file_is_not_read() {
        Integration northwind = added();
        answer = new ApiGateway.Answer(500, "text/plain", "e".repeat(5_000), 5_000, Duration.ZERO);
        McpToolbox.Result failed = toolbox.call(ref(northwind, "search_deals"), Map.of());
        assertThat(failed.error()).isTrue();
        assertThat(failed.text()).hasSize(CallMcpTools.MAX_ERROR_CHARACTERS + "\n(The answer was cut here.)".length());

        answer = new ApiGateway.Answer(200, "application/pdf", null, 900, Duration.ZERO);
        assertThat(toolbox.call(ref(northwind, "search_deals"), Map.of()).text())
                .contains("application/pdf (900 bytes), which ConXist doesn't read");
    }

    private Integration added() {
        Integration northwind = new Integration(UUID.randomUUID(), WORKSPACE, "Northwind",
                URI.create("https://api.northwind.test/v1"), ApiAuth.apiKey(ApiAuth.Place.HEADER, "X-Api-Key"), null,
                "a9F2", "/me", null, null, null, null, List.of(SEARCH, DELETE, UNTICKED), CLOCK.instant(),
                CLOCK.instant());
        stored.put(northwind.id(), northwind);
        return northwind;
    }

    private static McpToolbox.ToolRef ref(Integration api, String tool) {
        return new McpToolbox.ToolRef(api.id(), tool);
    }
}
