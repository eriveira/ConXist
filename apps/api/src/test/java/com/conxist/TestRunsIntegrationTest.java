package com.conxist;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    void settings_list_the_providers_and_set_where_new_blank_agents_start() throws Exception {
        String workspace = workspace("Providers");

        mvc.perform(get("/api/llm-providers").header(HEADER, workspace))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providers[0].id").value("anthropic"))
                .andExpect(jsonPath("$.providers[0].connected").value(true))
                .andExpect(jsonPath("$.providers[0].models[0].label").value("Claude Haiku 5.5"))
                .andExpect(jsonPath("$.providers[0].apiKey").doesNotExist())
                .andExpect(jsonPath("$.defaultForNewAgents.model").value("claude-sonnet-5-5"))
                .andExpect(jsonPath("$.defaultForNewAgents.effort").value("MEDIUM"));
        mvc.perform(put("/api/llm-providers/default").header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"anthropic\",\"model\":\"claude-opus-5-5\",\"effort\":\"HIGH\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultForNewAgents.model").value("claude-opus-5-5"));
        mvc.perform(put("/api/llm-providers/default").header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"anthropic\",\"model\":\"gpt-9\",\"effort\":\"HIGH\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("model.unavailable"));
        mvc.perform(get("/api/llm-providers").header(HEADER, workspace("Untouched")))
                .andExpect(jsonPath("$.defaultForNewAgents.model").value("claude-sonnet-5-5"));

        mvc.perform(post("/api/agents").header(HEADER, workspace).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Mine\"}"))
                .andExpect(jsonPath("$.draft.model.model").value("claude-opus-5-5"))
                .andExpect(jsonPath("$.draft.model.effort").value("HIGH"));
    }

    @Test
    void settings_list_every_model_anthropic_offers_and_a_ticked_one_can_run_while_an_unticked_one_cannot()
            throws Exception {
        String workspace = workspace("Allowed models");

        mvc.perform(get("/api/llm-providers/anthropic/models").header(HEADER, workspace))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(true))
                .andExpect(jsonPath("$.listError").isEmpty())
                .andExpect(jsonPath("$.models.length()").value(FakeClaudeServer.MODELS.size()))
                .andExpect(jsonPath("$.models[0].id").value("claude-fable-5-2"))
                .andExpect(jsonPath("$.models[0].label").value("Claude Fable 5.2"))
                .andExpect(jsonPath("$.models[0].allowed").value(false))
                .andExpect(jsonPath("$.models[1].allowed").value(true));

        mvc.perform(put("/api/llm-providers/anthropic/models/claude-fable-5-2").header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"allowed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.models[0].allowed").value(true));
        mvc.perform(get("/api/llm-providers").header(HEADER, workspace))
                .andExpect(jsonPath("$.providers[0].models[3].label").value("Claude Fable 5.2"));
        mvc.perform(get("/api/llm-providers").header(HEADER, workspace("Other")))
                .andExpect(jsonPath("$.providers[0].models.length()").value(3));

        String agent = agent(workspace, "Summary");
        mvc.perform(put("/api/agents/{id}/draft", agent).header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content(summaryDraft(1, "claude-fable-5-2")))
                .andExpect(jsonPath("$.problems").isEmpty());
        mvc.perform(put("/api/llm-providers/anthropic/models/claude-fable-5-2").header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"allowed\":false}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("model.in-use"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Summary")));

        mvc.perform(put("/api/llm-providers/anthropic/models/claude-haiku-5-5").header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"allowed\":false}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/agents/{id}/draft", agent).header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content(summaryDraft(2, "claude-haiku-5-5")))
                .andExpect(jsonPath("$.problems[0]").value("model-unavailable"));
        mvc.perform(put("/api/llm-providers/anthropic/models/claude-made-up").header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"allowed\":true}"))
                .andExpect(jsonPath("$.code").value("model.not-offered"));
        mvc.perform(put("/api/llm-providers/anthropic/models/{model}", "m".repeat(101)).header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"allowed\":true}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/llm-providers/anthropic/models/claude-opus-5").header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void a_run_uses_the_agents_own_model_and_a_model_that_cannot_run_is_refused() throws Exception {
        String workspace = workspace("Own model");
        String source = source(workspace, "Specs");
        String meeting = upload(workspace, source, "# Kickoff\nAna: we need guest checkout.");
        String agent = agent(workspace, "Summary");
        String run = "{\"sourceId\":\"" + source + "\",\"meetingId\":\"" + meeting + "\"}";

        mvc.perform(put("/api/agents/{id}/draft", agent).header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content(summaryDraft(1, "claude-opus-5-5")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.problems").isEmpty());
        mvc.perform(post("/api/agents/{id}/test-runs", agent).header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content(run))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.model").value("claude-opus-5-5"))
                .andExpect(jsonPath("$.effort").value("LOW"));
        org.assertj.core.api.Assertions.assertThat(claude.requests().getLast().path("model").asString())
                .isEqualTo("claude-opus-5-5");

        int before = claude.requests().size();
        mvc.perform(put("/api/agents/{id}/draft", agent).header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content(summaryDraft(2, "claude-retired")))
                .andExpect(jsonPath("$.problems[0]").value("model-unavailable"));
        mvc.perform(post("/api/agents/{id}/test-runs", agent).header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content(run))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("run.agent-not-ready"));
        org.assertj.core.api.Assertions.assertThat(claude.requests()).hasSize(before);
    }

    private static String summaryDraft(int revision, String model) {
        return """
                {"revision":%d,"draft":{"name":"Summary","purpose":"","instructions":"Sum it up.",
                "knowledgeSources":[],"skills":[],"tools":["GET_TRANSCRIPT"],
                "model":{"provider":"anthropic","model":"%s","effort":"LOW"},"style":"PRECISE",
                "accepts":["TRANSCRIPT"],"givesBack":"FREE_TEXT",
                "limits":{"maxSteps":6,"timeoutSeconds":120,"maxToolCalls":10}}}
                """.formatted(revision, model);
    }

    @Test
    void an_agents_draft_runs_on_a_meeting_and_answers_in_its_shape() throws Exception {
        String workspace = workspace("Runs");
        String source = source(workspace, "Specs");
        String meeting = upload(workspace, source, "# Kickoff\nAna: we need guest checkout.");
        String agent = agent(workspace, "Analyst");
        mvc.perform(put("/api/agents/{id}/draft", agent).header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"revision":1,"draft":{"name":"Analyst","purpose":"","instructions":"Find the requirements.",
                                "knowledgeSources":[],"skills":[],"tools":["GET_TRANSCRIPT"],
                                "model":{"provider":"anthropic","model":"claude-opus-5-5","effort":"HIGH"},
                                "style":"PRECISE","accepts":["TRANSCRIPT"],"givesBack":"REQUIREMENTS",
                                "limits":{"maxSteps":6,"timeoutSeconds":120,"maxToolCalls":10}}}
                                """))
                .andExpect(jsonPath("$.problems").isEmpty());

        mvc.perform(get("/api/test-runs/status").header(HEADER, workspace)).andExpect(jsonPath("$.ready").value(true));
        mvc.perform(post("/api/agents/{id}/test-runs", agent).header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceId\":\"" + source + "\",\"meetingId\":\"" + meeting + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("ANSWERED"))
                .andExpect(jsonPath("$.matchesShape").value(true))
                .andExpect(jsonPath("$.model").value("claude-opus-5-5"))
                .andExpect(jsonPath("$.effort").value("HIGH"))
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
        String theirAgent = agent(other, "Summary");
        String ownAgent = agent(owner, "Summary");
        for (String[] agent : new String[][] {{other, theirAgent}, {owner, ownAgent}}) {
            mvc.perform(put("/api/agents/{id}/draft", agent[1]).header(HEADER, agent[0])
                            .contentType(MediaType.APPLICATION_JSON).content(summaryDraft(1, "claude-sonnet-5-5")))
                    .andExpect(jsonPath("$.problems").isEmpty());
        }
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
        String agent = agent(workspace, "Empty");

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

    /** A new, empty agent. */
    private String agent(String workspace, String name) throws Exception {
        return JsonPath.read(mvc.perform(post("/api/agents").header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private String workspace(String name) throws Exception {
        return JsonPath.read(mvc.perform(post("/api/workspaces").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }
}
