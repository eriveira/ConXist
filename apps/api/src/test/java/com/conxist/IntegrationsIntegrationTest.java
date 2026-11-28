package com.conxist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import com.conxist.testing.FakeApiServer;
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
import org.springframework.test.web.servlet.ResultActions;

/**
 * Custom API integrations (ADR 0027) end to end: a real PostgreSQL, the real HTTP adapter and OpenAPI reader, and a
 * fake API on this computer.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresContainerConfiguration.class, FakePlaudConfiguration.class})
class IntegrationsIntegrationTest {

    private static final String HEADER = WorkspaceHeaderConfiguration.HEADER;
    private static final String KEY = FakeApiServer.KEY;

    @TempDir
    static Path uploads;

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("conxist.capture.storage-root", () -> uploads.toString());
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    FakeApiServer api;

    @Autowired
    JdbcClient jdbc;

    @Test
    void an_api_is_added_with_its_key_sealed_and_its_description_imported_into_grouped_tools() throws Exception {
        String workspace = workspace("Northwind");
        add(workspace, "Northwind", "wrong_key_0123").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("integration.sign-in-refused"));
        assertThat(rows(workspace)).isZero();

        String integration = id(add(workspace, "Northwind", KEY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Northwind"))
                .andExpect(jsonPath("$.auth.kind").value("API_KEY"))
                .andExpect(jsonPath("$.auth.name").value("X-Api-Key"))
                .andExpect(jsonPath("$.ending").value("a9F2"))
                .andExpect(jsonPath("$.status").value("WORKS"))
                .andExpect(jsonPath("$.tools").isEmpty())
                .andExpect(content().string(Matchers.not(Matchers.containsString(KEY)))));
        byte[] sealed = jdbc.sql("select secret_sealed from integrations where id = cast(:id as uuid)")
                .param("id", integration).query(byte[].class).single();
        assertThat(new String(sealed, StandardCharsets.ISO_8859_1)).doesNotContain(KEY);
        add(workspace, "northwind", KEY).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("integration.name-taken"));

        mvc.perform(post("/api/integrations/{id}/import", integration).header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"address\":\"" + api.address() + "/openapi.json\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(6))
                .andExpect(jsonPath("$.leftOut.length()").value(1))
                .andExpect(jsonPath("$.leftOut[0].operation").value("uploadAttachment"))
                .andExpect(jsonPath("$.integration.description.title").value("Northwind CRM API"))
                .andExpect(jsonPath("$.integration.tools[?(@.name=='list_contacts')].section").value("Contacts"))
                .andExpect(jsonPath("$.integration.tools[?(@.name=='list_contacts')].allowed").value(true))
                .andExpect(jsonPath("$.integration.tools[?(@.name=='get_contact')].path").value("/contacts/{id}"))
                .andExpect(jsonPath("$.integration.tools[?(@.name=='delete_contact')].allowed").value(false))
                .andExpect(jsonPath("$.integration.tools[?(@.name=='delete_contact')].reads").value(false))
                .andExpect(jsonPath("$.integration.tools[?(@.name=='search_deals')].allowed").value(false));

        // A tool written by hand, tried before it is saved, then saved.
        String tool = """
                {"name":"recent_contacts","section":"Contacts","method":"GET","path":"/contacts",
                "description":"The newest contacts.","inputs":[{"name":"limit","in":"QUERY","type":"integer",
                "required":true,"description":"How many"}],"reads":true,"allowed":true}
                """;
        mvc.perform(post("/api/integrations/{id}/try", integration).header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tool\":" + tool + ",\"inputs\":{\"limit\":2}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.request").value("GET /v1/contacts"))
                .andExpect(jsonPath("$.text").value(Matchers.containsString("Dana Globex")));
        assertThat(api.requests().getLast()).isEqualTo("GET /v1/contacts?limit=2");
        mvc.perform(post("/api/integrations/{id}/try", integration).header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tool\":" + tool + ",\"inputs\":{\"limit\":2,\"other\":1}}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("integration.try-input"));
        mvc.perform(post("/api/integrations/{id}/tools", integration).header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content(tool))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tools[?(@.name=='recent_contacts')].imported").value(false));

        // Importing again keeps the hand-written tool and the ticks.
        mvc.perform(post("/api/integrations/{id}/import", integration).header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"address\":\"" + api.address() + "/openapi.json\"}"))
                .andExpect(jsonPath("$.added").isEmpty())
                .andExpect(jsonPath("$.integration.tools.length()").value(7))
                .andExpect(jsonPath("$.integration.tools[?(@.name=='list_contacts')].allowed").value(true));

        mvc.perform(get("/api/integrations/{id}", integration).header(HEADER, workspace("Someone else")))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/integrations/{id}/check", integration).header(HEADER, workspace))
                .andExpect(jsonPath("$.works").value(true))
                .andExpect(jsonPath("$.request").value("GET /v1/me"));
        assertThat(api.requests()).noneMatch(request -> request.contains(KEY));

        // A logo set by hand: an uploaded image, not a file of another kind.
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 0, 0, 0, 13};
        mvc.perform(put("/api/integrations/{id}/logo", integration).header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"image\":\"data:image/png;base64,"
                                + Base64.getEncoder().encodeToString(png) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.icon").value(Matchers.startsWith("data:image/png;base64,")))
                .andExpect(jsonPath("$.logo.fetchedAt").isNotEmpty());
        mvc.perform(put("/api/integrations/{id}/logo", integration).header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"image\":\"data:image/png;base64,"
                                + Base64.getEncoder().encodeToString("<script>".getBytes()) + "\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("logo.not-image"));

        mvc.perform(delete("/api/integrations/{id}", integration).header(HEADER, workspace))
                .andExpect(status().isNoContent());
        assertThat(rows(workspace)).isZero();
    }

    @Test
    void an_agent_calls_an_api_tool_in_a_test_run_and_a_tool_or_api_in_use_stays() throws Exception {
        String workspace = workspace("API agent");
        String integration = id(add(workspace, "Northwind", KEY));
        mvc.perform(post("/api/integrations/{id}/import", integration).header(HEADER, workspace)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"address\":\"" + api.address() + "/openapi.json\"}")).andExpect(status().isOk());
        // searchDeals is a POST, so it starts as a change; the person says it only reads, then ticks it.
        String search = """
                {"name":"search_deals","section":"Deals","method":"POST","path":"/deals/search",
                "description":"Finds deals by company or stage","inputs":[{"name":"company","in":"BODY",
                "type":"string","required":false,"description":"Company name"}],"reads":true,"allowed":false}
                """;
        mvc.perform(put("/api/integrations/{id}/tools/{tool}", integration, "search_deals").header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content(search))
                .andExpect(jsonPath("$.tools[?(@.name=='search_deals')].reads").value(true));
        allow(workspace, integration, "\"list_contacts\",\"search_deals\"").andExpect(status().isOk());

        String agent = JsonPath.read(mvc.perform(post("/api/agents").header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Deal reader\"}"))
                .andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(put("/api/agents/{id}/draft", agent).header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content(draft(1, integration, "search_deals")))
                .andExpect(jsonPath("$.problems").isEmpty());
        String source = JsonPath.read(mvc.perform(post("/api/sources").header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"provider\":\"UPLOAD\",\"name\":\"Notes\"}"))
                .andReturn().getResponse().getContentAsString(), "$.id");
        String meeting = JsonPath.read(mvc.perform(multipart("/api/sources/{id}/files", source)
                        .file(new MockMultipartFile("file", "n.md", "text/markdown", "Ana: Globex.".getBytes()))
                        .header(HEADER, workspace))
                .andReturn().getResponse().getContentAsString(), "$.id");
        int before = api.requests().size();

        mvc.perform(post("/api/agents/{id}/test-runs", agent).header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceId\":\"" + source + "\",\"meetingId\":\"" + meeting + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("ANSWERED"))
                .andExpect(jsonPath("$.steps[1].kind").value("TOOL"))
                .andExpect(jsonPath("$.steps[1].ok").value(true));
        List<String> calls = api.requests().subList(before, api.requests().size());
        assertThat(calls).containsExactly("POST /v1/deals/search {\"company\":\"Globex\"}");

        allow(workspace, integration, "\"list_contacts\"").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("integration.tool-in-use"));
        mvc.perform(delete("/api/integrations/{id}/tools/{tool}", integration, "search_deals").header(HEADER, workspace))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/integrations/{id}/tools/{tool}", integration, "search_deals").header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content(search.replace("\"reads\":true", "\"reads\":false")))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/integrations/{id}", integration).header(HEADER, workspace))
                .andExpect(jsonPath("$.tools[?(@.name=='search_deals')].usedBy[0].agentName").value("Deal reader"));
        mvc.perform(delete("/api/integrations/{id}", integration).header(HEADER, workspace))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("integration.in-use"));

        mvc.perform(put("/api/agents/{id}/draft", agent).header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content(draft(2, integration, "delete_contact")))
                .andExpect(jsonPath("$.problems[0]").value("mcp-tool-unavailable"));
        mvc.perform(put("/api/agents/{id}/draft", agent).header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content(draft(3, null, null)))
                .andExpect(jsonPath("$.problems").isEmpty());
        mvc.perform(delete("/api/integrations/{id}", integration).header(HEADER, workspace))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleting_a_workspace_deletes_its_apis() throws Exception {
        String workspace = workspace("Gone APIs");
        id(add(workspace, "Northwind", KEY));
        assertThat(rows(workspace)).isOne();
        mvc.perform(delete("/api/workspaces/{id}", workspace).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Gone APIs\"}"))
                .andExpect(status().isNoContent());
        assertThat(rows(workspace)).isZero();
    }

    private static String draft(int revision, String integration, String tool) {
        String tools = integration == null ? "[]"
                : "[{\"serverId\":\"" + integration + "\",\"tool\":\"" + tool + "\"}]";
        return """
                {"revision":%d,"draft":{"name":"Deal reader","purpose":"","instructions":"Find the deal.",
                "skills":[],"tools":[],"mcpTools":%s,
                "model":{"provider":"anthropic","model":"claude-sonnet-5-5","effort":"MEDIUM"}}}
                """.formatted(revision, tools);
    }

    private ResultActions add(String workspace, String name, String key) throws Exception {
        return mvc.perform(post("/api/integrations").header(HEADER, workspace)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"%s","address":"%s","auth":"API_KEY","keyPlace":"HEADER","keyName":"X-Api-Key",
                        "secret":"%s","checkPath":"/me"}
                        """.formatted(name, api.address(), key)));
    }

    private ResultActions allow(String workspace, String integration, String tools) throws Exception {
        return mvc.perform(put("/api/integrations/{id}/allowed", integration).header(HEADER, workspace)
                .contentType(MediaType.APPLICATION_JSON).content("{\"allowedTools\":[" + tools + "]}"));
    }

    private static String id(ResultActions added) throws Exception {
        return JsonPath.read(added.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),
                "$.id");
    }

    private long rows(String workspace) {
        return jdbc.sql("select count(*) from integrations where workspace_id = cast(:w as uuid)")
                .param("w", workspace).query(Long.class).single();
    }

    private String workspace(String name) throws Exception {
        return JsonPath.read(mvc.perform(post("/api/workspaces").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }
}
