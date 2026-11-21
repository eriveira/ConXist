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
                        .content("{\"template\":\"REQUIREMENTS_ANALYST\",\"name\":\"Analista\",\"language\":\"ES\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.draft.givesBack").value("REQUIREMENTS"))
                .andExpect(jsonPath("$.draft.tools[0]").value("GET_TRANSCRIPT"))
                .andExpect(jsonPath("$.problems").isEmpty())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");

        String v1 = mvc.perform(post("/api/agents/{id}/publish", id).header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":1}"))
                .andExpect(jsonPath("$.publishedVersion").value(1))
                .andExpect(jsonPath("$.draftPublished").value(true))
                .andReturn().getResponse().getContentAsString();
        int revision = JsonPath.read(v1, "$.revision");

        mvc.perform(put("/api/agents/{id}/draft", id).header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content(draft(revision, "Requirements")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.draft.name").value("Requirements"))
                .andExpect(jsonPath("$.draft.model").value("FAST"))
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
                        .content("{\"template\":\"BLANK\",\"name\":\"Mine\",\"language\":\"EN\"}"))
                .andExpect(jsonPath("$.problems[0]").value("instructions-required"))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");

        mvc.perform(post("/api/agents/{id}/publish", id).header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":1}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("agent.not-ready"));
        mvc.perform(put("/api/agents/{id}/draft", id).header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(draft(1, "Mine").replace("\"maxSteps\":6", "\"maxSteps\":99")))
                .andExpect(jsonPath("$.code").value("agent.limits-steps"));
        mvc.perform(post("/api/agents").header(HEADER, workspace).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"template\":\"BLANK\",\"name\":\"MINE\",\"language\":\"EN\"}"))
                .andExpect(jsonPath("$.code").value("agent.name-taken"));
    }

    @Test
    void an_agent_of_one_workspace_is_out_of_reach_from_another() throws Exception {
        String owner = workspace("Owner of agents");
        String other = workspace("Someone else");
        String created = mvc.perform(post("/api/agents").header(HEADER, owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"template\":\"MEETING_SUMMARIZER\",\"name\":\"Summary\",\"language\":\"EN\"}"))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");

        mvc.perform(get("/api/agents/{id}", id).header(HEADER, other))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("agent.not-found"));
        mvc.perform(post("/api/agents/{id}/archive", id).header(HEADER, other)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":1}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/agents").header(HEADER, other)).andExpect(jsonPath("$").isEmpty());
        mvc.perform(post("/api/agents/{id}/publish", id).header(HEADER, owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":1}"))
                .andExpect(jsonPath("$.publishedVersion").value(1));

        String[][] attempts = {
                {"PUT", "/api/agents/{id}/draft", draft(2, "Taken over")},
                {"POST", "/api/agents/{id}/publish", "{\"revision\":2}"},
                {"GET", "/api/agents/{id}/versions/1", null},
                {"POST", "/api/agents/{id}/versions/1/restore", "{\"revision\":2}"},
                {"POST", "/api/agents/{id}/archive", "{\"revision\":2}"},
                {"POST", "/api/agents/{id}/unarchive", "{\"revision\":2}"}};
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
                .andExpect(jsonPath("$.revision").value(2))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.draft.name").value("Summary"));
    }

    @Test
    void an_agent_cannot_publish_reading_another_workspaces_source() throws Exception {
        String owner = workspace("Reader");
        String other = workspace("Holder of the source");
        String source = JsonPath.read(mvc.perform(post("/api/sources").header(HEADER, other)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"provider\":\"UPLOAD\",\"name\":\"Theirs\"}"))
                .andReturn().getResponse().getContentAsString(), "$.id");
        String id = JsonPath.read(mvc.perform(post("/api/agents").header(HEADER, owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"template\":\"MEETING_SUMMARIZER\",\"name\":\"Reader\",\"language\":\"EN\"}"))
                .andReturn().getResponse().getContentAsString(), "$.id");

        mvc.perform(put("/api/agents/{id}/draft", id).header(HEADER, owner).contentType(MediaType.APPLICATION_JSON)
                        .content(draft(1, "Reader").replace("\"knowledgeSources\":[]",
                                "\"knowledgeSources\":[\"" + source + "\"]")))
                .andExpect(jsonPath("$.problems[0]").value("source-missing"));
        mvc.perform(post("/api/agents/{id}/publish", id).header(HEADER, owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":2}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("agent.source-missing"));
    }

    private static String draft(int revision, String name) {
        return """
                {"revision":%d,"draft":{"name":"%s","purpose":"Finds requirements","instructions":"Find them.",
                "knowledgeSources":[],"tools":["GET_TRANSCRIPT"],"model":"FAST","style":"PRECISE",
                "accepts":["TRANSCRIPT"],"givesBack":"REQUIREMENTS",
                "limits":{"maxSteps":6,"timeoutSeconds":120,"maxToolCalls":10}}}
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
