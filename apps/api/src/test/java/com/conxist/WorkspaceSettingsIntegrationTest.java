package com.conxist;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Workspace Settings against a real PostgreSQL: General (name and description) and Variables (ADR 0018). */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresContainerConfiguration.class, FakePlaudConfiguration.class})
class WorkspaceSettingsIntegrationTest {

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
    void a_workspace_takes_a_new_name_and_description_but_not_a_name_another_one_has() throws Exception {
        String id = workspace("Settings one");
        workspace("Settings taken");

        mvc.perform(put("/api/workspaces/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" Settings renamed \",\"description\":\"Checkout team\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Settings renamed"))
                .andExpect(jsonPath("$.description").value("Checkout team"));
        mvc.perform(put("/api/workspaces/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"settings TAKEN\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("workspace.name-taken"));
        mvc.perform(put("/api/workspaces/{id}", "00000000-0000-0000-0000-00000000dead")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Ghost\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/workspaces"))
                .andExpect(jsonPath("$[?(@.id == '" + id + "')].name").value("Settings renamed"));
    }

    @Test
    void variables_belong_to_one_workspace_and_keep_unique_names() throws Exception {
        String workspace = workspace("Variables one");
        String other = workspace("Variables two");

        String topics = variable(workspace, "{\"name\":\"topics\",\"value\":\"Checkout, Payments\"}");
        variable(workspace, "{\"name\":\"company_name\",\"value\":\"Acme Inc.\"}");
        mvc.perform(post("/api/variables").header(HEADER, workspace).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Topics\",\"value\":\"x\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("variable.name-taken"));
        mvc.perform(post("/api/variables").header(HEADER, workspace).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"company name\",\"value\":\"x\"}"))
                .andExpect(jsonPath("$.code").value("variable.name-invalid"));
        mvc.perform(get("/api/variables").header(HEADER, workspace))
                .andExpect(jsonPath("$[0].name").value("company_name"))
                .andExpect(jsonPath("$[1].value").value("Checkout, Payments"));

        mvc.perform(put("/api/variables/{id}", topics).header(HEADER, other).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"mine\",\"value\":\"x\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/variables/{id}", topics).header(HEADER, other)).andExpect(status().isNotFound());
        mvc.perform(get("/api/variables").header(HEADER, other)).andExpect(jsonPath("$.length()").value(0));

        mvc.perform(put("/api/variables/{id}", topics).header(HEADER, workspace).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Company_Name\",\"value\":\"x\"}"))
                .andExpect(jsonPath("$.code").value("variable.name-taken"));
        mvc.perform(put("/api/variables/{id}", topics).header(HEADER, workspace).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"areas\",\"value\":\"Web, Mobile\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("areas"));
        mvc.perform(delete("/api/variables/{id}", topics).header(HEADER, workspace)).andExpect(status().isNoContent());
        mvc.perform(get("/api/variables").header(HEADER, workspace)).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/variables")).andExpect(jsonPath("$.code").value("workspace.required"));
    }

    private String workspace(String name) throws Exception {
        return JsonPath.read(mvc.perform(post("/api/workspaces").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private String variable(String workspace, String json) throws Exception {
        return JsonPath.read(mvc.perform(post("/api/variables").header(HEADER, workspace)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }
}
