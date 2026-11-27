package com.conxist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import com.conxist.testing.FakeMcpServer;
import com.conxist.testing.FakeMcpServers;
import com.conxist.testing.FakePlaudConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponentsBuilder;

/** MCP servers end to end (ADR 0017): a real PostgreSQL, the real HTTP gateway, and fake MCP servers on localhost. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresContainerConfiguration.class, FakePlaudConfiguration.class})
class McpServersIntegrationTest {

    private static final String HEADER = WorkspaceHeaderConfiguration.HEADER;

    @TempDir
    static Path uploads;

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("conxist.capture.storage-root", () -> uploads.toString());
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    FakeMcpServers fakes;

    @Autowired
    JdbcClient jdbc;

    @Test
    void a_public_server_is_added_with_its_logo_and_only_the_tools_a_person_ticked() throws Exception {
        String workspace = workspace("Public MCP");
        String connection = connect(workspace, "{\"address\":\"" + fakes.open().mcpUrl() + "\"}")
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.auth").value("NONE"))
                .andExpect(jsonPath("$.suggestedName").value("Acme Docs"))
                .andExpect(jsonPath("$.icon").value(Matchers.startsWith("data:image/png;base64,")))
                .andExpect(jsonPath("$.tools.length()").value(3))
                .andExpect(jsonPath("$.tools[0].name").value("search_docs"))
                .andExpect(jsonPath("$.tools[0].readOnly").value(true))
                .andExpect(jsonPath("$.tools[2].name").value("create_doc"))
                .andExpect(jsonPath("$.tools[2].readOnly").value(false))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(connection, "$.id");

        String server = JsonPath.read(add(workspace, id, "Acme Docs", "\"search_docs\"")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("WORKS"))
                .andExpect(jsonPath("$.tools[0].allowed").value(true))
                .andExpect(jsonPath("$.tools[1].allowed").value(false))
                .andReturn().getResponse().getContentAsString(), "$.id");

        String again = JsonPath.read(connect(workspace, "{\"address\":\"" + fakes.open().mcpUrl() + "\"}")
                .andReturn().getResponse().getContentAsString(), "$.id");
        add(workspace, again, "acme docs", "").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("mcp.name-taken"));
        add(workspace, id, "Acme 2", "").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("mcp.connection-gone"));

        mvc.perform(get("/api/mcps").header(HEADER, workspace))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Acme Docs"));
        mvc.perform(get("/api/mcps/{id}", server).header(HEADER, workspace("Someone else")))
                .andExpect(status().isNotFound());

        fakes.open().addTool("list_spaces");
        mvc.perform(post("/api/mcps/{id}/check", server).header(HEADER, workspace))
                .andExpect(jsonPath("$.works").value(true))
                .andExpect(jsonPath("$.newTools[0]").value("list_spaces"))
                .andExpect(jsonPath("$.server.tools[0].allowed").value(true))
                .andExpect(jsonPath("$.server.tools[3].name").value("list_spaces"))
                .andExpect(jsonPath("$.server.tools[3].allowed").value(false));
    }

    @Test
    void a_server_that_wants_a_token_asks_for_one_and_keeps_it_encrypted() throws Exception {
        String workspace = workspace("Token MCP");
        String address = fakes.token().mcpUrl().toString();
        connect(workspace, "{\"address\":\"" + address + "\"}").andExpect(jsonPath("$.status").value("TOKEN_NEEDED"));
        connect(workspace, "{\"address\":\"" + address + "\",\"token\":\"wrong-token\"}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("mcp.token-refused"));
        String connection = connect(workspace, "{\"address\":\"" + address + "\",\"token\":\"" + FakeMcpServer.TOKEN
                + "\"}")
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.auth").value("TOKEN"))
                // It names no logo, so the site's own icon stands in.
                .andExpect(jsonPath("$.icon").value(Matchers.startsWith("data:image/png;base64,")))
                .andExpect(content().string(Matchers.not(Matchers.containsString(FakeMcpServer.TOKEN))))
                .andReturn().getResponse().getContentAsString();

        String server = JsonPath.read(add(workspace, JsonPath.read(connection, "$.id"), "Docs", "\"get_doc\"")
                .andExpect(content().string(Matchers.not(Matchers.containsString(FakeMcpServer.TOKEN))))
                .andReturn().getResponse().getContentAsString(), "$.id");

        byte[] sealed = jdbc.sql("select credential_sealed from mcp_servers where id = cast(:id as uuid)")
                .param("id", server).query(byte[].class).single();
        assertThat(new String(sealed, StandardCharsets.ISO_8859_1)).doesNotContain(FakeMcpServer.TOKEN);
        mvc.perform(post("/api/mcps/{id}/check", server).header(HEADER, workspace))
                .andExpect(jsonPath("$.works").value(true));
    }

    @Test
    void a_server_with_its_own_sign_in_signs_in_refreshes_and_signs_in_again() throws Exception {
        String workspace = workspace("OAuth MCP");
        FakeMcpServer fake = fakes.oauth();
        String started = connect(workspace, "{\"address\":\"" + fake.mcpUrl() + "\"}")
                .andExpect(jsonPath("$.status").value("SIGNING_IN"))
                .andExpect(jsonPath("$.signInUrl").value(Matchers.containsString("code_challenge_method=S256")))
                .andReturn().getResponse().getContentAsString();
        String connection = JsonPath.read(started, "$.id");
        signIn(JsonPath.read(started, "$.signInUrl"));
        mvc.perform(get("/api/mcps/connections/{id}", connection).header(HEADER, workspace))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.auth").value("OAUTH"));
        String server = JsonPath.read(add(workspace, connection, "Acme", "\"search_docs\"")
                .andReturn().getResponse().getContentAsString(), "$.id");

        fake.expireAccessTokens();
        mvc.perform(post("/api/mcps/{id}/check", server).header(HEADER, workspace))
                .andExpect(jsonPath("$.works").value(true));

        fake.signOutEverywhere();
        mvc.perform(post("/api/mcps/{id}/check", server).header(HEADER, workspace))
                .andExpect(jsonPath("$.works").value(false))
                .andExpect(jsonPath("$.error").value("sign-in-needed"))
                .andExpect(jsonPath("$.server.status").value("SIGN_IN_NEEDED"));

        String again = connect(workspace, "{\"serverId\":\"" + server + "\"}")
                .andExpect(jsonPath("$.status").value("SIGNING_IN"))
                .andReturn().getResponse().getContentAsString();
        signIn(JsonPath.read(again, "$.signInUrl"));
        mvc.perform(post("/api/mcps/{id}/credential", server).header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"connectionId\":\"" + JsonPath.read(again, "$.id") + "\"}"))
                .andExpect(jsonPath("$.status").value("WORKS"))
                .andExpect(jsonPath("$.tools[0].allowed").value(true));

        fake.cancelNextSignIn();
        String cancelled = connect(workspace, "{\"serverId\":\"" + server + "\"}")
                .andReturn().getResponse().getContentAsString();
        signIn(JsonPath.read(cancelled, "$.signInUrl"));
        mvc.perform(get("/api/mcps/connections/{id}", (String) JsonPath.read(cancelled, "$.id")).header(HEADER, workspace))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.error").value("sign-in-refused"));
    }

    @Test
    void an_address_off_https_or_on_a_private_network_is_refused() throws Exception {
        String workspace = workspace("Addresses");
        connect(workspace, "{\"address\":\"http://203.0.113.5/mcp\"}").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("mcp.address-not-https"));
        connect(workspace, "{\"address\":\"https://10.0.0.8/mcp\"}").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("mcp.address-private"));
        connect(workspace, "{\"address\":\"https://169.254.169.254/latest\"}")
                .andExpect(jsonPath("$.code").value("mcp.address-private"));
        connect(workspace, "{\"address\":\"not an address\"}").andExpect(jsonPath("$.code").value("mcp.address-invalid"));
    }

    @Test
    void an_agent_runs_an_allowed_mcp_tool_and_a_tool_or_server_in_use_stays() throws Exception {
        String workspace = workspace("MCP agent");
        String connection = JsonPath.read(connect(workspace, "{\"address\":\"" + fakes.open().mcpUrl() + "\"}")
                .andReturn().getResponse().getContentAsString(), "$.id");
        String server = JsonPath.read(add(workspace, connection, "Acme Docs", "\"search_docs\",\"get_doc\"")
                .andReturn().getResponse().getContentAsString(), "$.id");
        String agent = JsonPath.read(mvc.perform(post("/api/agents").header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Researcher\"}"))
                .andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(put("/api/agents/{id}/draft", agent).header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content(draft(1, server, "search_docs")))
                .andExpect(jsonPath("$.problems").isEmpty())
                .andExpect(jsonPath("$.draft.mcpTools[0].tool").value("search_docs"));
        String source = JsonPath.read(mvc.perform(post("/api/sources").header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"provider\":\"UPLOAD\",\"name\":\"Notes\"}"))
                .andReturn().getResponse().getContentAsString(), "$.id");
        String meeting = JsonPath.read(mvc.perform(multipart("/api/sources/{id}/files", source)
                        .file(new MockMultipartFile("file", "n.md", "text/markdown", "Ana: checkout.".getBytes()))
                        .header(HEADER, workspace))
                .andReturn().getResponse().getContentAsString(), "$.id");
        int before = fakes.open().calls.size();

        mvc.perform(post("/api/agents/{id}/test-runs", agent).header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceId\":\"" + source + "\",\"meetingId\":\"" + meeting + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("ANSWERED"))
                .andExpect(jsonPath("$.steps[1].kind").value("TOOL"))
                .andExpect(jsonPath("$.steps[1].ok").value(true));
        assertThat(fakes.open().calls.subList(before, fakes.open().calls.size()))
                .containsExactly("search_docs {\"query\":\"checkout\"}");

        mvc.perform(put("/api/mcps/{id}", server).header(HEADER, workspace).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Acme Docs\",\"allowedTools\":[\"get_doc\"]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("mcp.tool-in-use"));
        mvc.perform(get("/api/mcps", server).header(HEADER, workspace))
                .andExpect(jsonPath("$[0].tools[0].usedBy[0].agentName").value("Researcher"))
                .andExpect(jsonPath("$[0].tools[0].usedBy[0].where").value("DRAFT"));
        mvc.perform(delete("/api/mcps/{id}", server).header(HEADER, workspace))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("mcp.in-use"));

        mvc.perform(put("/api/mcps/{id}", server).header(HEADER, workspace).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Acme Docs\",\"allowedTools\":[\"search_docs\"]}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/agents/{id}/draft", agent).header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content(draft(2, server, "get_doc")))
                .andExpect(jsonPath("$.problems[0]").value("mcp-tool-unavailable"));
        mvc.perform(put("/api/agents/{id}/draft", agent).header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content(draft(3, null, null)))
                .andExpect(jsonPath("$.problems").isEmpty());
        mvc.perform(delete("/api/mcps/{id}", server).header(HEADER, workspace)).andExpect(status().isNoContent());
    }

    private static String draft(int revision, String server, String tool) {
        String tools = server == null ? "[]" : "[{\"serverId\":\"" + server + "\",\"tool\":\"" + tool + "\"}]";
        return """
                {"revision":%d,"draft":{"name":"Researcher","purpose":"","instructions":"Find the spec.",
                "knowledgeSources":[],"skills":[],"tools":[],"mcpTools":%s,
                "model":{"provider":"anthropic","model":"claude-sonnet-5-5","effort":"MEDIUM"},
                "style":"PRECISE","accepts":["TRANSCRIPT"],"givesBack":"FREE_TEXT",
                "limits":{"maxSteps":6,"timeoutSeconds":120,"maxToolCalls":10}}}
                """.formatted(revision, tools);
    }

    /** Opens the server's sign-in page, which approves at once, and follows it back to ConXist's callback. */
    private void signIn(String signInUrl) throws Exception {
        HttpResponse<Void> page = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()
                .send(HttpRequest.newBuilder(URI.create(signInUrl)).build(), HttpResponse.BodyHandlers.discarding());
        assertThat(page.statusCode()).isEqualTo(302);
        var back = UriComponentsBuilder.fromUriString(page.headers().firstValue("Location").orElseThrow()).build();
        assertThat(back.getPath()).isEqualTo("/api/mcps/callback");
        var callback = get("/api/mcps/callback");
        back.getQueryParams().forEach((name, values) -> callback.queryParam(name,
                java.net.URLDecoder.decode(values.getFirst(), StandardCharsets.UTF_8)));
        mvc.perform(callback)
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", Matchers.containsString("default-src 'none'")))
                .andExpect(content().string(Matchers.containsString("Back to ConXist")));
    }

    private org.springframework.test.web.servlet.ResultActions connect(String workspace, String body) throws Exception {
        return mvc.perform(post("/api/mcps/connections").header(HEADER, workspace)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private org.springframework.test.web.servlet.ResultActions add(String workspace, String connection, String name,
            String allowed) throws Exception {
        return mvc.perform(post("/api/mcps").header(HEADER, workspace).contentType(MediaType.APPLICATION_JSON)
                .content("{\"connectionId\":\"" + connection + "\",\"name\":\"" + name + "\",\"allowedTools\":["
                        + allowed + "]}"));
    }

    private String workspace(String name) throws Exception {
        return JsonPath.read(mvc.perform(post("/api/workspaces").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }
}
