package com.conxist;

import static org.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** The whole slice against a real PostgreSQL: add sources, capture, and see it arrive in the hub. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresContainerConfiguration.class)
class CaptureFlowIntegrationTest {

    @TempDir
    static Path uploads;

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("conxist.capture.storage-root", () -> uploads.toString());
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Test
    void an_uploaded_file_reaches_the_hub_waiting_for_review() throws Exception {
        String sourceId = addSource("{\"provider\":\"UPLOAD\",\"name\":\"Product specs\"}");
        MockMultipartFile file = new MockMultipartFile("file", "notes.md", "text/markdown",
                "# Kickoff\nEvery source connects to the hub.".getBytes(StandardCharsets.UTF_8));

        mvc.perform(multipart("/api/sources/{id}/files", sourceId).file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("notes.md"))
                .andExpect(jsonPath("$.origin").value("UPLOAD"));

        mvc.perform(get("/api/sources/{id}/documents", sourceId))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].text").doesNotExist());
        awaitHubItem(sourceId, "DOCUMENT", "PENDING_REVIEW");
        awaitHubItem(sourceId, "SOURCE", "CONNECTED");
        mvc.perform(get("/api/hub"))
                .andExpect(jsonPath("$.pendingReview").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.latest[0].workspaceId").doesNotExist());
    }

    @Test
    void a_source_of_another_workspace_is_out_of_reach() throws Exception {
        UUID otherWorkspace = UUID.randomUUID();
        UUID foreignSource = UUID.randomUUID();
        jdbc.sql("""
                insert into sources (id, workspace_id, provider, name, added_at)
                values (:id, :workspaceId, 'UPLOAD', 'Their files', now())
                """).param("id", foreignSource).param("workspaceId", otherWorkspace).update();
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain",
                "Their notes".getBytes(StandardCharsets.UTF_8));

        mvc.perform(multipart("/api/sources/{id}/files", foreignSource).file(file))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("source.not-found"));
        mvc.perform(get("/api/sources/{id}/documents", foreignSource)).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/sources")).andExpect(jsonPath("$[?(@.name=='Their files')]").isEmpty());
    }

    @Test
    void the_database_refuses_a_document_filed_under_another_workspaces_source() {
        String sourceId = jdbc.sql("""
                insert into sources (id, workspace_id, provider, name, added_at)
                values (gen_random_uuid(), gen_random_uuid(), 'UPLOAD', 'Isolated', now()) returning id::text
                """).query(String.class).single();

        assertThatThrownBy(() -> jdbc.sql("""
                insert into captured_documents (id, workspace_id, source_id, title, media_type, size_bytes,
                  text_content, origin, origin_reference, captured_at)
                values (gen_random_uuid(), gen_random_uuid(), cast(:sourceId as uuid), 'x', 'text/plain', 1, 'x',
                  'UPLOAD', 'k', now())
                """).param("sourceId", sourceId).update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void a_second_source_with_the_same_name_is_refused_with_a_code() throws Exception {
        addSource("{\"provider\":\"UPLOAD\",\"name\":\"Duplicated\"}");

        mvc.perform(post("/api/sources").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"UPLOAD\",\"name\":\"duplicated\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("source.name-taken"));
    }

    @Test
    void every_response_carries_a_request_id() throws Exception {
        mvc.perform(get("/api/providers")).andExpect(header().exists("X-Request-Id"));
        mvc.perform(get("/api/providers").header("X-Request-Id", "trace-12345678"))
                .andExpect(header().string("X-Request-Id", "trace-12345678"));
        mvc.perform(get("/api/providers").header("X-Request-Id", "bad id\nwith newline"))
                .andExpect(header().string("X-Request-Id", not("bad id\nwith newline")));
    }

    @Test
    void a_recorder_sync_imports_each_recording_once() throws Exception {
        String account = JsonPath.read(mvc.perform(post("/api/recorders/plaud/sign-in"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.label");
        String sourceId = addSource("{\"provider\":\"PLAUD\",\"name\":\"Team meetings\",\"accountLabel\":\""
                + account + "\"}");

        mvc.perform(post("/api/sources/{id}/sync", sourceId))
                .andExpect(jsonPath("$.imported").value(2));
        mvc.perform(post("/api/sources/{id}/sync", sourceId))
                .andExpect(jsonPath("$.imported").value(0))
                .andExpect(jsonPath("$.alreadyImported").value(2));
    }

    @Test
    void a_provider_that_is_coming_soon_cannot_be_added() throws Exception {
        mvc.perform(post("/api/sources").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"GITHUB\",\"name\":\"Code\",\"accountLabel\":\"me\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("GitHub is coming soon."))
                .andExpect(jsonPath("$.code").value("source.coming-soon"));
    }

    @Test
    void an_unsupported_file_is_refused_with_a_plain_message() throws Exception {
        String sourceId = addSource("{\"provider\":\"UPLOAD\",\"name\":\"Mixed files\"}");
        MockMultipartFile program = new MockMultipartFile("file", "setup.exe", "application/octet-stream",
                new byte[] {0x4d, 0x5a, 0x00, 0x01});

        mvc.perform(multipart("/api/sources/{id}/files", sourceId).file(program))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(startsWith("This file type")));
    }

    @Test
    void lists_every_provider_with_its_availability() throws Exception {
        mvc.perform(get("/api/providers"))
                .andExpect(jsonPath("$", hasSize(11)))
                .andExpect(jsonPath("$[?(@.provider=='UPLOAD')].availability").value("LIVE"))
                .andExpect(jsonPath("$[?(@.provider=='PLAUD')].availability").value("PREVIEW"));
    }

    private String addSource(String json) throws Exception {
        String body = mvc.perform(post("/api/sources").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private void awaitHubItem(String sourceId, String type, String expectedStatus) {
        await().atMost(Duration.ofSeconds(10)).until(() -> jdbc.sql("""
                        select count(*) from hub_items
                        where source_id = cast(:sourceId as uuid) and item_type = :type and status = :status
                        """)
                .param("sourceId", sourceId).param("type", type).param("status", expectedStatus)
                .query(Long.class).single() == 1);
    }
}
