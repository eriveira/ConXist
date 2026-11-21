package com.conxist;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import com.conxist.testing.FakeClaudeServer;
import com.conxist.testing.FakePlaudConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** A test run end to end: a real PostgreSQL, the real Claude adapter, and a fake Anthropic API on localhost. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresContainerConfiguration.class, FakePlaudConfiguration.class})
class TestRunsIntegrationTest {

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
    FakeClaudeServer claude;

    @Test
    void an_agents_draft_runs_on_a_meeting_and_answers_in_its_shape() throws Exception {
        String workspace = workspace("Runs");
        String source = source(workspace, "Specs");
        String meeting = upload(workspace, source, "# Kickoff\nAna: we need guest checkout.");
        String agent = agent(workspace, "{\"template\":\"REQUIREMENTS_ANALYST\",\"name\":\"Analyst\",\"language\":\"EN\"}");

        mvc.perform(get("/api/test-runs/status").header(HEADER, workspace)).andExpect(jsonPath("$.ready").value(true));
        mvc.perform(post("/api/agents/{id}/test-runs", agent).header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceId\":\"" + source + "\",\"meetingId\":\"" + meeting + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("ANSWERED"))
                .andExpect(jsonPath("$.matchesShape").value(true))
                .andExpect(jsonPath("$.model").value("claude-opus-5-5"))
                .andExpect(jsonPath("$.preset").value("THOROUGH"))
                .andExpect(jsonPath("$.structured.requirements[0].type").value("BUG"))
                .andExpect(jsonPath("$.steps[0].kind").value("MODEL"));

        String system = claude.requests().getLast().path("system").asString();
        org.assertj.core.api.Assertions.assertThat(system).startsWith("You are an agent in ConXist");
        org.assertj.core.api.Assertions.assertThat(claude.requests().getLast().path("messages").get(0)
                .path("content").asString()).contains("guest checkout");
    }

    @Test
    void a_meeting_or_agent_of_another_workspace_does_not_run() throws Exception {
        String owner = workspace("Runs owner");
        String other = workspace("Someone else's runs");
        String source = source(owner, "Calls");
        String meeting = upload(owner, source, "Private notes.");
        String theirAgent = agent(other, "{\"template\":\"MEETING_SUMMARIZER\",\"name\":\"Summary\",\"language\":\"EN\"}");
        String ownAgent = agent(owner, "{\"template\":\"MEETING_SUMMARIZER\",\"name\":\"Summary\",\"language\":\"EN\"}");
        int before = claude.requests().size();

        mvc.perform(post("/api/agents/{id}/test-runs", theirAgent).header(HEADER, other)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceId\":\"" + source + "\",\"meetingId\":\"" + meeting + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("run.meeting-not-found"));
        mvc.perform(post("/api/agents/{id}/test-runs", ownAgent).header(HEADER, other)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceId\":\"" + source + "\",\"meetingId\":\"" + meeting + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("agent.not-found"));
        org.assertj.core.api.Assertions.assertThat(claude.requests()).hasSize(before);
    }

    @Test
    void a_draft_that_is_not_ready_is_refused_before_anything_is_sent() throws Exception {
        String workspace = workspace("Not ready");
        String source = source(workspace, "Notes");
        String meeting = upload(workspace, source, "Notes.");
        String agent = agent(workspace, "{\"template\":\"BLANK\",\"name\":\"Empty\",\"language\":\"EN\"}");

        mvc.perform(post("/api/agents/{id}/test-runs", agent).header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceId\":\"" + source + "\",\"meetingId\":\"" + meeting + "\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("run.agent-not-ready"));
    }

    private String upload(String workspace, String source, String text) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "notes.md", "text/markdown",
                text.getBytes(StandardCharsets.UTF_8));
        return JsonPath.read(mvc.perform(multipart("/api/sources/{id}/files", source).file(file)
                        .header(HEADER, workspace))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private String source(String workspace, String name) throws Exception {
        return JsonPath.read(mvc.perform(post("/api/sources").header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"UPLOAD\",\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private String agent(String workspace, String body) throws Exception {
        return JsonPath.read(mvc.perform(post("/api/agents").header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private String workspace(String name) throws Exception {
        return JsonPath.read(mvc.perform(post("/api/workspaces").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }
}
