package com.conxist;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import com.conxist.testing.FakePlaudConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Agents against a real PostgreSQL: create, edit the draft, publish versions, restore, and each workspace apart. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresContainerConfiguration.class, FakePlaudConfiguration.class})
class AgentsIntegrationTest {

    private static final String HEADER = WorkspaceHeaderConfiguration.HEADER;

    @TempDir
    static Path uploads;

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("conxist.capture.storage-root", () -> uploads.toString());
    }

    @Autowired
    MockMvc mvc;

    @Test
    void an_agent_is_drafted_published_changed_and_restored() throws Exception {
        String workspace = workspace("Agents");
        String created = mvc.perform(post("/api/agents").header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Analista\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.draft.instructions").value(""))
                .andExpect(jsonPath("$.draft.tools").isEmpty())
                .andExpect(jsonPath("$.draft.model.model").value("claude-sonnet-5-5"))
                .andExpect(jsonPath("$.problems[0]").value("instructions-required"))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");
        mvc.perform(put("/api/agents/{id}/draft", id).header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content(draft(1, "Analista")))
                .andExpect(jsonPath("$.problems").isEmpty());

        String v1 = mvc.perform(post("/api/agents/{id}/publish", id).header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":2}"))
                .andExpect(jsonPath("$.publishedVersion").value(1))
                .andExpect(jsonPath("$.draftPublished").value(true))
                .andReturn().getResponse().getContentAsString();
        int revision = JsonPath.read(v1, "$.revision");

        mvc.perform(put("/api/agents/{id}/draft", id).header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content(draft(revision, "Requirements")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.draft.name").value("Requirements"))
                .andExpect(jsonPath("$.draft.model.model").value("claude-haiku-5-5"))
                .andExpect(jsonPath("$.draft.model.effort").value("LOW"))
                .andExpect(jsonPath("$.draftPublished").value(false));
        mvc.perform(put("/api/agents/{id}/draft", id).header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content(draft(revision, "Stale tab")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("agent.changed"));

        mvc.perform(get("/api/agents/{id}/versions/1", id).header(HEADER, workspace))
                .andExpect(jsonPath("$.definition.name").value("Analista"));
        mvc.perform(post("/api/agents/{id}/versions/1/restore", id).header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":" + (revision + 1) + "}"))
                .andExpect(jsonPath("$.draft.name").value("Analista"))
                .andExpect(jsonPath("$.draftPublished").value(true));
        mvc.perform(get("/api/agents").header(HEADER, workspace))
                .andExpect(jsonPath("$[0].name").value("Analista"))
                .andExpect(jsonPath("$[0].publishedVersion").value(1));
    }

    @Test
    void a_draft_that_breaks_a_rule_is_refused_with_its_code() throws Exception {
        String workspace = workspace("Rules");
        String created = mvc.perform(post("/api/agents").header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Mine\"}"))
                .andExpect(jsonPath("$.problems[0]").value("instructions-required"))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");

        mvc.perform(post("/api/agents/{id}/publish", id).header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":1}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("agent.not-ready"));
        mvc.perform(post("/api/agents").header(HEADER, workspace).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"MINE\"}"))
                .andExpect(jsonPath("$.code").value("agent.name-taken"));
    }

    @Test
    void an_agent_of_one_workspace_is_out_of_reach_from_another() throws Exception {
        String owner = workspace("Owner of agents");
        String other = workspace("Someone else");
        String id = ready(owner, "Summary");

        mvc.perform(get("/api/agents/{id}", id).header(HEADER, other))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("agent.not-found"));
        mvc.perform(post("/api/agents/{id}/archive", id).header(HEADER, other)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":1}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/agents").header(HEADER, other)).andExpect(jsonPath("$").isEmpty());
        mvc.perform(post("/api/agents/{id}/publish", id).header(HEADER, owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":2}"))
                .andExpect(jsonPath("$.publishedVersion").value(1));

        String[][] attempts = {
                {"PUT", "/api/agents/{id}/draft", draft(3, "Taken over")},
                {"POST", "/api/agents/{id}/publish", "{\"revision\":3}"},
                {"GET", "/api/agents/{id}/versions/1", null},
                {"POST", "/api/agents/{id}/versions/1/restore", "{\"revision\":3}"},
                {"POST", "/api/agents/{id}/archive", "{\"revision\":3}"},
                {"POST", "/api/agents/{id}/unarchive", "{\"revision\":3}"}};
        for (String[] attempt : attempts) {
            var request = request(HttpMethod.valueOf(attempt[0]), attempt[1], id).header(HEADER, other);
            if (attempt[2] != null) {
                request.contentType(MediaType.APPLICATION_JSON).content(attempt[2]);
            }
            mvc.perform(request)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("agent.not-found"));
        }
        mvc.perform(get("/api/agents/{id}", id).header(HEADER, owner))
                .andExpect(jsonPath("$.revision").value(3))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.draft.name").value("Summary"));
    }

    @Test
    void a_draft_from_an_older_page_saves_without_the_fields_the_agent_no_longer_has() throws Exception {
        String workspace = workspace("Older page");
        String id = JsonPath.read(mvc.perform(post("/api/agents").header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Old\"}"))
                .andReturn().getResponse().getContentAsString(), "$.id");

        mvc.perform(put("/api/agents/{id}/draft", id).header(HEADER, workspace).contentType(MediaType.APPLICATION_JSON)
                        .content(draft(1, "Old").replace("\"tools\"", "\"style\":\"CREATIVE\",\"knowledgeSources\":[],"
                                + "\"limits\":{\"maxSteps\":6},\"tools\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.draft.style").doesNotExist())
                .andExpect(jsonPath("$.draft.knowledgeSources").doesNotExist())
                .andExpect(jsonPath("$.problems").isEmpty());
    }

    /** A new agent with instructions and a tool, saved once, so it is ready to publish at revision 2. */
    private String ready(String workspace, String name) throws Exception {
        String id = JsonPath.read(mvc.perform(post("/api/agents").header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\"}"))
                .andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(put("/api/agents/{id}/draft", id).header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content(draft(1, name)))
                .andExpect(status().isOk());
        return id;
    }

    private static String draft(int revision, String name) {
        return """
                {"revision":%d,"draft":{"name":"%s","purpose":"Finds requirements","instructions":"Find them.",
                "tools":["GET_TRANSCRIPT"],"model":{"provider":"anthropic","model":"claude-haiku-5-5","effort":"LOW"}}}
                """.formatted(revision, name);
    }

    private String workspace(String name) throws Exception {
        String body = mvc.perform(post("/api/workspaces").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }
}
