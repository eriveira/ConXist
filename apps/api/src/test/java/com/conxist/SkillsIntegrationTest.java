package com.conxist;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import java.util.Map;
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
import tools.jackson.databind.json.JsonMapper;

/** Skills against a real PostgreSQL: import, publish, pin to an agent, the person's language, workspaces apart. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresContainerConfiguration.class, FakePlaudConfiguration.class})
class SkillsIntegrationTest {

    private static final String HEADER = WorkspaceHeaderConfiguration.HEADER;
    private static final String SKILL_MD = """
            ---
            name: release-notes
            description: When writing release notes from merged work.
            ---
            # Release notes

            Read the meeting with get_transcript. Group changes by New, Improved and Fixed.
            """;

    @TempDir
    static Path uploads;

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("conxist.capture.storage-root", () -> uploads.toString());
    }

    @Autowired
    MockMvc mvc;

    @Test
    void an_imported_skill_is_published_and_an_agent_follows_a_fixed_version() throws Exception {
        String workspace = workspace("Skills");
        String imported = mvc.perform(post("/api/skills/import").header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content(json("markdown", SKILL_MD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.source").value("IMPORTED"))
                .andExpect(jsonPath("$.draft.name").value("Release notes"))
                .andExpect(jsonPath("$.draft.needs[0]").value("GET_TRANSCRIPT"))
                .andExpect(jsonPath("$.problems").isEmpty())
                .andReturn().getResponse().getContentAsString();
        String skillId = JsonPath.read(imported, "$.id");

        mvc.perform(post("/api/skills/{id}/publish", skillId).header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":1,\"note\":\"Imported\"}"))
                .andExpect(jsonPath("$.publishedVersion").value(1))
                .andExpect(jsonPath("$.versions[0].note").value("Imported"));
        mvc.perform(put("/api/skills/{id}/draft", skillId).header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"revision":2,"draft":{"name":"Release notes","description":"When writing notes.",
                                "instructions":"Group by New and Fixed.","needs":[]}}"""))
                .andExpect(jsonPath("$.revision").value(3))
                .andExpect(jsonPath("$.draftPublished").value(false));

        String agent = mvc.perform(post("/api/agents").header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Notes\"}"))
                .andReturn().getResponse().getContentAsString();
        String agentId = JsonPath.read(agent, "$.id");
        mvc.perform(put("/api/agents/{id}/draft", agentId).header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(agentDraft(1, "[{\"skillId\":\"" + skillId + "\",\"version\":1}]", "[]")))
                .andExpect(jsonPath("$.skills[0].name").value("Release notes"))
                .andExpect(jsonPath("$.skills[0].version").value(1))
                .andExpect(jsonPath("$.skills[0].newestVersion").value(1))
                .andExpect(jsonPath("$.problems[0]").value("skill-needs-tool"));
        mvc.perform(put("/api/agents/{id}/draft", agentId).header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(agentDraft(2, "[{\"skillId\":\"" + skillId + "\",\"version\":1}]",
                                "[\"GET_TRANSCRIPT\"]")))
                .andExpect(jsonPath("$.problems").isEmpty());
        mvc.perform(post("/api/agents/{id}/publish", agentId).header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":3}"))
                .andExpect(jsonPath("$.publishedVersion").value(1));
        mvc.perform(get("/api/agents/{id}/versions/1", agentId).header(HEADER, workspace))
                .andExpect(jsonPath("$.definition.skills[0].version").value(1));
        mvc.perform(get("/api/agents").header(HEADER, workspace))
                .andExpect(jsonPath("$[0].skills[0].skillId").value(skillId));
    }

    @Test
    void a_new_workspace_has_no_skills() throws Exception {
        mvc.perform(get("/api/skills").header(HEADER, workspace("Fresh")))
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void a_skill_of_one_workspace_is_out_of_reach_from_another() throws Exception {
        String owner = workspace("Owner of skills");
        String other = workspace("Someone else's skills");
        String created = mvc.perform(post("/api/skills/import").header(HEADER, owner)
                        .contentType(MediaType.APPLICATION_JSON).content(json("markdown", SKILL_MD)))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");
        mvc.perform(post("/api/skills/{id}/publish", id).header(HEADER, owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":1}"))
                .andExpect(jsonPath("$.publishedVersion").value(1));

        String[][] attempts = {
                {"GET", "/api/skills/{id}", null},
                {"PUT", "/api/skills/{id}/draft", "{\"revision\":2,\"draft\":{\"name\":\"Taken over\"}}"},
                {"POST", "/api/skills/{id}/publish", "{\"revision\":2}"},
                {"GET", "/api/skills/{id}/versions/1", null},
                {"POST", "/api/skills/{id}/versions/1/restore", "{\"revision\":2}"},
                {"POST", "/api/skills/{id}/archive", "{\"revision\":2}"},
                {"POST", "/api/skills/{id}/unarchive", "{\"revision\":2}"}};
        for (String[] attempt : attempts) {
            var request = request(HttpMethod.valueOf(attempt[0]), attempt[1], id).header(HEADER, other);
            if (attempt[2] != null) {
                request.contentType(MediaType.APPLICATION_JSON).content(attempt[2]);
            }
            mvc.perform(request)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("skill.not-found"));
        }

        mvc.perform(get("/api/skills").header(HEADER, other))
                .andExpect(jsonPath("$").isEmpty());

        String agent = mvc.perform(post("/api/agents").header(HEADER, other)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Borrower\"}"))
                .andReturn().getResponse().getContentAsString();
        String agentId = JsonPath.read(agent, "$.id");
        mvc.perform(put("/api/agents/{id}/draft", agentId).header(HEADER, other)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(agentDraft(1, "[{\"skillId\":\"" + id + "\",\"version\":1}]", "[\"GET_TRANSCRIPT\"]")))
                .andExpect(jsonPath("$.skills").isEmpty())
                .andExpect(jsonPath("$.problems[0]").value("skill-missing"));
        mvc.perform(post("/api/agents/{id}/publish", agentId).header(HEADER, other)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":2}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("agent.skill-missing"));
        mvc.perform(get("/api/agents").header(HEADER, owner)).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/skills/{id}", id).header(HEADER, owner))
                .andExpect(jsonPath("$.revision").value(2))
                .andExpect(jsonPath("$.draft.name").value("Release notes"));
    }

    @Test
    void a_skill_an_agent_follows_is_kept_until_the_agent_is_gone_then_both_are_deleted_with_their_versions()
            throws Exception {
        String workspace = workspace("Deleting");
        String other = workspace("Not the owner");
        String skillId = JsonPath.read(mvc.perform(post("/api/skills/import").header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content(json("markdown", SKILL_MD)))
                .andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(post("/api/skills/{id}/publish", skillId).header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":1}"))
                .andExpect(jsonPath("$.revision").value(2));
        String agentId = JsonPath.read(mvc.perform(post("/api/agents").header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Notes\"}"))
                .andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(put("/api/agents/{id}/draft", agentId).header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(agentDraft(1, "[{\"skillId\":\"" + skillId + "\",\"version\":1}]",
                                "[\"GET_TRANSCRIPT\"]")))
                .andExpect(jsonPath("$.revision").value(2));
        mvc.perform(post("/api/agents/{id}/publish", agentId).header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":2}"))
                .andExpect(jsonPath("$.revision").value(3));

        mvc.perform(get("/api/skills/{id}/uses", skillId).header(HEADER, workspace))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].kind").value("AGENT"))
                .andExpect(jsonPath("$[0].id").value(agentId))
                .andExpect(jsonPath("$[0].where").value("BOTH"));
        mvc.perform(delete("/api/skills/{id}", skillId).param("revision", "2").header(HEADER, workspace))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("skill.in-use"));

        mvc.perform(delete("/api/agents/{id}", agentId).param("revision", "3").header(HEADER, other))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/agents/{id}", agentId).param("revision", "2").header(HEADER, workspace))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("agent.changed"));
        mvc.perform(get("/api/agents/{id}/uses", agentId).header(HEADER, workspace))
                .andExpect(jsonPath("$").isEmpty());
        mvc.perform(delete("/api/agents/{id}", agentId).param("revision", "3").header(HEADER, workspace))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/agents/{id}", agentId).header(HEADER, workspace))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/skills/{id}/uses", skillId).header(HEADER, workspace))
                .andExpect(jsonPath("$").isEmpty());
        mvc.perform(delete("/api/skills/{id}", skillId).param("revision", "2").header(HEADER, other))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/skills/{id}", skillId).param("revision", "2").header(HEADER, workspace))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/skills/{id}", skillId).header(HEADER, workspace))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/skills").header(HEADER, workspace))
                .andExpect(jsonPath("$").isEmpty());
    }

    private static String agentDraft(int revision, String skills, String tools) {
        return """
                {"revision":%d,"draft":{"name":"Agent","purpose":"","instructions":"Write the notes.",
                "knowledgeSources":[],"skills":%s,"tools":%s,"model":{"provider":"anthropic","model":"claude-haiku-5-5","effort":"LOW"},"style":"PRECISE",
                "accepts":["TRANSCRIPT"],"givesBack":"FREE_TEXT",
                "limits":{"maxSteps":6,"timeoutSeconds":120,"maxToolCalls":10}}}
                """.formatted(revision, skills, tools);
    }

    private static String json(String key, String value) {
        return JsonMapper.builder().build().writeValueAsString(Map.of(key, value));
    }

    private String workspace(String name) throws Exception {
        String body = mvc.perform(post("/api/workspaces").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }
}
