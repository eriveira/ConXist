package com.conxist;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import com.conxist.testing.FakePlaudConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Workspaces against a real PostgreSQL: create, list, and each request acting only for the workspace it names. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresContainerConfiguration.class, FakePlaudConfiguration.class})
class WorkspacesIntegrationTest {

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
    void a_new_workspace_is_listed_and_counts_its_sources() throws Exception {
        String id = create("{\"name\":\"  Acme Product \",\"description\":\"Specs and meetings for Acme.\"}");

        mvc.perform(post("/api/sources").header(HEADER, id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"UPLOAD\",\"name\":\"Specs\"}"))
                .andExpect(status().isCreated());

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> mvc.perform(get("/api/workspaces"))
                .andExpect(jsonPath("$[?(@.id=='" + id + "')].name").value("Acme Product"))
                .andExpect(jsonPath("$[?(@.id=='" + id + "')].description").value("Specs and meetings for Acme."))
                .andExpect(jsonPath("$[?(@.id=='" + id + "')].sourceCount").value(1)));
    }

    @Test
    void each_workspace_sees_only_its_own_sources() throws Exception {
        String first = create("{\"name\":\"Payments\"}");
        String second = create("{\"name\":\"Mobile\"}");
        mvc.perform(post("/api/sources").header(HEADER, first).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"UPLOAD\",\"name\":\"Payment specs\"}"))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/sources").header(HEADER, first))
                .andExpect(jsonPath("$[?(@.name=='Payment specs')]").isNotEmpty());
        mvc.perform(get("/api/sources").header(HEADER, second))
                .andExpect(jsonPath("$[?(@.name=='Payment specs')]").isEmpty());
    }

    @Test
    void a_source_of_one_workspace_is_out_of_reach_from_another() throws Exception {
        String owner = create("{\"name\":\"Owner\"}");
        String other = create("{\"name\":\"Other\"}");
        String body = mvc.perform(post("/api/sources").header(HEADER, owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"UPLOAD\",\"name\":\"Private notes\"}"))
                .andReturn().getResponse().getContentAsString();
        String sourceId = JsonPath.read(body, "$.id");
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain",
                "Owner's notes".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/sources/{id}/files", sourceId).file(file).header(HEADER, owner))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/sources/{id}/documents", sourceId).header(HEADER, other))
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(multipart("/api/sources/{id}/files", sourceId).file(file).header(HEADER, other))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/sources/{id}/sync", sourceId).header(HEADER, other))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/sources/{id}/documents", sourceId).header(HEADER, owner))
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void a_request_that_names_no_workspace_or_an_unknown_one_is_refused() throws Exception {
        mvc.perform(get("/api/sources"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("workspace.required"));
        mvc.perform(get("/api/sources").header(HEADER, "not-an-id"))
                .andExpect(jsonPath("$.code").value("workspace.required"));
        mvc.perform(get("/api/sources").header(HEADER, "00000000-0000-0000-0000-00000000dead"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("workspace.not-found"));
    }

    @Test
    void a_workspace_needs_a_name_that_is_not_taken() throws Exception {
        create("{\"name\":\"Research\"}");

        mvc.perform(post("/api/workspaces").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"research\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("workspace.name-taken"));
        mvc.perform(post("/api/workspaces").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"  \"}"))
                .andExpect(jsonPath("$.code").value("workspace.name-required"))
                .andExpect(jsonPath("$.detail").value("Give your workspace a name to continue."));
        mvc.perform(post("/api/workspaces").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Long\",\"description\":\"" + "x".repeat(501) + "\"}"))
                .andExpect(jsonPath("$.code").value("workspace.description-too-long"));
    }

    private String create(String json) throws Exception {
        String body = mvc.perform(post("/api/workspaces").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }
}
