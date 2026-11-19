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
import com.conxist.testing.FakePlaudConfiguration;
import com.conxist.testing.FakePlaudMcpServer;
import com.conxist.testing.FakePlaudServer;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
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
@Import({PostgresContainerConfiguration.class, FakePlaudConfiguration.class, WorkspaceHeaderConfiguration.class})
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

    @Autowired
    FakePlaudServer plaud;

    @Autowired
    java.util.List<ApplicationRunner> runners;

    @Autowired
    FakePlaudMcpServer plaudMcp;

<<<<<<< HEAD
    @Autowired
    com.conxist.capture.application.SyncHistory history;

    @Autowired
    com.conxist.platform.CurrentWorkspace workspace;

    @Test
    void the_import_history_keeps_90_days_and_returns_the_newest_changes_first() throws Exception {
        UUID sourceId = UUID.fromString(addSource("{\"provider\":\"UPLOAD\",\"name\":\"History\"}"));
        java.time.Instant now = java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        java.util.function.BiFunction<Integer, com.conxist.capture.application.SyncHistory.Outcome,
                com.conxist.capture.application.SyncHistory.SyncRun> run = (daysAgo, outcome) ->
                new com.conxist.capture.application.SyncHistory.SyncRun(UUID.randomUUID(), workspace.id(), sourceId,
                        now.minus(Duration.ofDays(daysAgo)), now.minus(Duration.ofDays(daysAgo)),
                        com.conxist.capture.application.SyncHistory.StartedBy.SCHEDULE, outcome,
                        outcome == com.conxist.capture.application.SyncHistory.Outcome.IMPORTED ? 1 : 0, 0, 0, 0, null);
        history.record(run.apply(100, com.conxist.capture.application.SyncHistory.Outcome.IMPORTED));
        history.record(run.apply(3, com.conxist.capture.application.SyncHistory.Outcome.IMPORTED));
        history.record(run.apply(2, com.conxist.capture.application.SyncHistory.Outcome.UP_TO_DATE));
        history.record(run.apply(1, com.conxist.capture.application.SyncHistory.Outcome.IMPORTED));

        history.forgetBefore(UUID.randomUUID(), now.minus(com.conxist.capture.application.SyncHistory.KEEP));
        org.assertj.core.api.Assertions.assertThat(history.recent(workspace.id(), sourceId, false, 10)).hasSize(4);
        history.forgetBefore(workspace.id(), now.minus(com.conxist.capture.application.SyncHistory.KEEP));

        org.assertj.core.api.Assertions.assertThat(history.recent(workspace.id(), sourceId, false, 10))
                .extracting(r -> r.startedAt().isAfter(now.minus(Duration.ofDays(4)))).containsOnly(true).hasSize(3);
        org.assertj.core.api.Assertions.assertThat(history.recent(workspace.id(), sourceId, true, 1))
                .singleElement().satisfies(r -> org.assertj.core.api.Assertions.assertThat(r.startedAt())
                        .isEqualTo(now.minus(Duration.ofDays(1))));
        org.assertj.core.api.Assertions.assertThat(history.recent(workspace.id(), sourceId, true, 10)).hasSize(2);
=======
    @BeforeEach
    void workspace() {
        WorkspaceHeaderConfiguration.ensure(jdbc);
>>>>>>> a5837f7 (feat(workspaces): create and list workspaces; each request names its workspace)
    }

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
        assertNoPlaintextFor(sourceId, "Every source connects");
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
    void answers_only_requests_addressed_to_this_machine() throws Exception {
        mvc.perform(get("/api/sources").header("Host", "rebound.attacker.example")).andExpect(status().isForbidden());
        mvc.perform(post("/api/recorders/plaud/keys").header("Origin", "https://attacker.example"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/sources").header("Origin", "http://localhost:5173")).andExpect(status().isOk());
    }

    @Test
    void requests_the_client_got_wrong_keep_a_4xx_status_and_a_code() throws Exception {
        mvc.perform(get("/api/sources/{id}/documents", "not-a-uuid"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("request.invalid"));
        mvc.perform(post("/api/sources").contentType(MediaType.APPLICATION_JSON).content("{\"provider\":\"NOPE\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("request.invalid"));
        mvc.perform(post("/api/sources/{id}/files", UUID.randomUUID()).contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().is4xxClientError());
        mvc.perform(get("/api/nothing-here"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("request.not-found"));
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
    void a_plaud_team_connects_with_a_conxist_key_and_imports_each_meeting_once() throws Exception {
        String key = mvc.perform(post("/api/recorders/plaud/keys"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pem").value(startsWith("-----BEGIN PUBLIC KEY-----")))
                .andExpect(jsonPath("$.privateKey").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String keyId = JsonPath.read(key, "$.keyId");
        String credential = "{\"clientId\":\"client_it\",\"keyId\":\"" + keyId + "\"}";

        mvc.perform(post("/api/recorders/plaud/test").contentType(MediaType.APPLICATION_JSON).content(credential))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("plaud.not-accepted"));
        plaud.register("client_it", JsonPath.read(key, "$.pem"));
        mvc.perform(post("/api/recorders/plaud/test").contentType(MediaType.APPLICATION_JSON).content(credential))
                .andExpect(jsonPath("$.teamName").value(FakePlaudServer.TEAM_NAME));

        String sourceId = addSource("{\"provider\":\"PLAUD\",\"name\":\"Team meetings\",\"accountLabel\":\""
                + FakePlaudServer.TEAM_NAME + "\"}");
        mvc.perform(post("/api/sources/{id}/plaud-team", sourceId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"client_it\",\"keyId\":\"" + keyId
                                + "\",\"startFrom\":\"LAST_30_DAYS\",\"includeSummaries\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.clientId").doesNotExist());

        mvc.perform(post("/api/sources/{id}/sync", sourceId))
                .andExpect(jsonPath("$.imported").value(3))
                .andExpect(jsonPath("$.waitingForTranscript").value(1));
        mvc.perform(post("/api/sources/{id}/sync", sourceId))
                .andExpect(jsonPath("$.imported").value(0));
        mvc.perform(get("/api/recorders/plaud/connections"))
                .andExpect(jsonPath("$[?(@.sourceId=='" + sourceId + "')].lastCheckedAt").isNotEmpty());
        awaitHubItem(sourceId, "DOCUMENT", "PENDING_REVIEW", 3);
        String sealed = jdbc.sql("select encode(private_key_sealed, 'escape') from recorder_keys where key_id = :id")
                .param("id", keyId).query(String.class).single();
        org.assertj.core.api.Assertions.assertThat(sealed).doesNotContain("PRIVATE KEY");
        assertNoPlaintextFor(sourceId, "Text of");
    }

    @Test
    void text_captured_before_encryption_is_sealed_on_start_and_still_readable() throws Exception {
        String sourceId = addSource("{\"provider\":\"UPLOAD\",\"name\":\"Old notes\"}");
        jdbc.sql("""
                insert into captured_documents (id, workspace_id, source_id, title, media_type, size_bytes,
                  text_content, origin, origin_reference, captured_at)
                select :id, workspace_id, id, 'old.txt', 'text/plain', 9, 'Old notes', 'UPLOAD', 'old', now()
                from sources where id = :sourceId
                """).param("id", UUID.randomUUID()).param("sourceId", UUID.fromString(sourceId)).update();

        for (ApplicationRunner runner : runners) {
            runner.run(new DefaultApplicationArguments());
        }

        assertNoPlaintextFor(sourceId, "Old notes");
        mvc.perform(get("/api/sources/{id}/documents", sourceId))
                .andExpect(jsonPath("$[0].title").value("old.txt"))
                .andExpect(jsonPath("$[0].textLength").value(9));
    }

    private void assertNoPlaintextFor(String sourceId, String phrase) {
        org.assertj.core.api.Assertions.assertThat(jdbc.sql("""
                select count(*) from captured_documents
                where source_id = :id and (text_content is not null or position(convert_to(:phrase, 'UTF8') in text_sealed) > 0)
                """).param("id", UUID.fromString(sourceId)).param("phrase", phrase).query(Long.class).single())
                .isZero();
        org.assertj.core.api.Assertions.assertThat(jdbc.sql(
                "select count(*) from captured_documents where source_id = :id and text_sealed is not null")
                .param("id", UUID.fromString(sourceId)).query(Long.class).single()).isPositive();
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

    @Test
    void a_person_signs_in_with_plaud_and_their_meetings_import_once_and_keep_coming_after_a_token_refresh()
            throws Exception {
        String signInId = signInWithPlaud();
        mvc.perform(get("/api/recorders/plaud/sign-ins/{id}", signInId))
                .andExpect(jsonPath("$.status").value("SIGNED_IN"))
                .andExpect(jsonPath("$.accountLabel").value(FakePlaudMcpServer.ACCOUNT_EMAIL));
        String sourceId = addSource("{\"provider\":\"PLAUD\",\"name\":\"My meetings\",\"accountLabel\":\""
                + FakePlaudMcpServer.ACCOUNT_EMAIL + "\"}");

        mvc.perform(get("/api/recorders/plaud/sign-ins/{id}/recordings", signInId))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.recordings", hasSize(5)))
                .andExpect(jsonPath("$.recordings[0].id").value("of_demo_5"))
                .andExpect(jsonPath("$.recordings[0].lengthSeconds").value(60))
                .andExpect(jsonPath("$.capped").value(false))
                .andExpect(jsonPath("$.max").value(500));
        mvc.perform(get("/api/recorders/plaud/sign-ins/{id}/recordings", signInId)
                        .param("from", "2026-01-02").param("to", "2026-01-01"))
                .andExpect(jsonPath("$.code").value("plaud.dates-reversed"));
        mvc.perform(post("/api/sources/{id}/plaud-account", sourceId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"signInId\":\"" + signInId + "\",\"recordingIds\":[\"bad id!\"],"
                                + "\"importNew\":true,\"includeSummaries\":true}"))
                .andExpect(status().isBadRequest());
        // The 50-day-old retro is picked too: picking reaches past any date limit.
        mvc.perform(post("/api/sources/{id}/plaud-account", sourceId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"signInId\":\"" + signInId + "\",\"recordingIds\":[\"of_demo_1\",\"of_demo_4\","
                                + "\"of_demo_5\"],\"importNew\":true,\"includeSummaries\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("ACCOUNT"))
                .andExpect(jsonPath("$.label").value(FakePlaudMcpServer.ACCOUNT_EMAIL));
        mvc.perform(post("/api/sources/{id}/sync", sourceId))
                .andExpect(jsonPath("$.imported").value(2))
                .andExpect(jsonPath("$.waitingForTranscript").value(1))
                .andExpect(jsonPath("$.queued").value(0));
        org.assertj.core.api.Assertions.assertThat(jdbc.sql(
                "select file_id from plaud_recording_picks where source_id = :id")
                .param("id", UUID.fromString(sourceId)).query(String.class).list()).containsExactly("of_demo_5");
        plaudMcp.expireAccessTokens();
        mvc.perform(post("/api/sources/{id}/sync", sourceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(0));
        awaitHubItem(sourceId, "DOCUMENT", "PENDING_REVIEW", 2);
        assertNoPlaintextFor(sourceId, "ship sign-in first");
        org.assertj.core.api.Assertions.assertThat(jdbc.sql("""
                select count(*) from plaud_account_tokens where source_id = :id
                  and position(convert_to('at-', 'UTF8') in tokens_sealed) = 0
                """).param("id", UUID.fromString(sourceId)).query(Long.class).single()).isEqualTo(1L);
        // The sign-in cannot be used for a second source.
        String other = addSource("{\"provider\":\"PLAUD\",\"name\":\"Copy\",\"accountLabel\":\"copy\"}");
        mvc.perform(post("/api/sources/{id}/plaud-account", other).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"signInId\":\"" + signInId + "\",\"recordingIds\":[],\"importNew\":true,"
                                + "\"includeSummaries\":false}"))
                .andExpect(status().isNotFound());

        plaudMcp.signOutEverywhere();
        mvc.perform(post("/api/sources/{id}/sync", sourceId))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("plaud.signed-out"));
        mvc.perform(get("/api/recorders/plaud/connections"))
                .andExpect(jsonPath("$[?(@.sourceId=='" + sourceId + "')].status").value("PAUSED"));
        mvc.perform(post("/api/sources/{id}/plaud-account/sign-in", sourceId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"signInId\":\"" + signInWithPlaud() + "\"}"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        mvc.perform(post("/api/sources/{id}/sync", sourceId)).andExpect(status().isOk());

        // The import history: every check, with who started it, and the problem code of the one that failed.
        mvc.perform(get("/api/sources/{id}/syncs", sourceId).param("all", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.outcome=='IMPORTED')].imported", org.hamcrest.Matchers.hasItem(2)))
                .andExpect(jsonPath("$[?(@.outcome=='FAILED')].problemCode",
                        org.hamcrest.Matchers.hasItem("plaud.signed-out")))
                .andExpect(jsonPath("$[?(@.outcome=='UP_TO_DATE')].startedBy", org.hamcrest.Matchers.hasItem("PERSON")));
        mvc.perform(get("/api/sources/{id}/syncs", sourceId))
                .andExpect(jsonPath("$[?(@.outcome=='UP_TO_DATE')]").isEmpty());

        // One meeting, read back as summary and transcript lines.
        String listed = mvc.perform(get("/api/sources/{id}/documents", sourceId))
                .andExpect(jsonPath("$[?(@.title=='Weekly product sync')].recordedAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        java.util.List<String> ids = JsonPath.read(listed, "$[?(@.title=='Weekly product sync')].id");
        mvc.perform(get("/api/sources/{id}/documents/{doc}", sourceId, ids.getFirst()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.origin").value("RECORDING"))
                .andExpect(jsonPath("$.meeting.lines[0].speaker").value("Alex"))
                .andExpect(jsonPath("$.meeting.lines[0].text").value("Let's ship sign-in first."))
                .andExpect(jsonPath("$.meeting.summaries[0]", org.hamcrest.Matchers.containsString("Sign in with Plaud")));
        mvc.perform(get("/api/sources/{id}/documents/{doc}", other, ids.getFirst()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("document.not-found"));
    }

    @Test
    void lists_at_most_the_newest_500_recordings_and_says_there_are_more() throws Exception {
        java.time.Instant old = java.time.Instant.parse("2001-06-01T12:00:00Z");
        for (int i = 0; i <= 500; i++) {
            plaudMcp.add(new com.conxist.testing.FakePlaudServer.Meeting("of_cap_" + i, "Old " + i, old.minusSeconds(60L * i), true, "",
                    java.util.List.of("Alex: old")));
        }
        try {
            String signInId = signInWithPlaud();
            mvc.perform(get("/api/recorders/plaud/sign-ins/{id}/recordings", signInId)
                            .param("from", "2001-01-01").param("to", "2001-12-31"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.recordings", hasSize(500)))
                    .andExpect(jsonPath("$.recordings[0].id").value("of_cap_0"))
                    .andExpect(jsonPath("$.capped").value(true));
        } finally {
            plaudMcp.remove(meeting -> meeting.id().startsWith("of_cap_"));
        }
    }

    @Test
    void a_plaud_sign_in_of_another_workspace_lists_and_connects_nothing() throws Exception {
        String signInId = signInWithPlaud();
        jdbc.sql("update plaud_sign_ins set workspace_id = :other where id = :id")
                .param("other", UUID.randomUUID()).param("id", UUID.fromString(signInId)).update();
        String sourceId = addSource("{\"provider\":\"PLAUD\",\"name\":\"Not theirs\",\"accountLabel\":\"them@acme.test\"}");

        mvc.perform(get("/api/recorders/plaud/sign-ins/{id}/recordings", signInId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("plaud.sign-in-not-found"));
        mvc.perform(post("/api/sources/{id}/plaud-account", sourceId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"signInId\":\"" + signInId + "\",\"recordingIds\":[],"
                                + "\"importNew\":true,\"includeSummaries\":false}"))
                .andExpect(status().is4xxClientError());
        org.assertj.core.api.Assertions.assertThat(jdbc.sql(
                "select count(*) from plaud_account_connections where source_id = :id")
                .param("id", UUID.fromString(sourceId)).query(Integer.class).single()).isZero();
    }

    @Test
    void a_cancelled_plaud_sign_in_connects_nothing() throws Exception {
        plaudMcp.cancelNextSignIn();

        String signInId = signInWithPlaud();

        mvc.perform(get("/api/recorders/plaud/sign-ins/{id}", signInId))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    /** Starts a sign-in, lets the stand-in Plaud page answer, and brings its answer to the callback. */
    private String signInWithPlaud() throws Exception {
        String started = mvc.perform(post("/api/recorders/plaud/sign-ins"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String authorizationUrl = JsonPath.read(started, "$.authorizationUrl");
        java.net.http.HttpResponse<Void> page = java.net.http.HttpClient.newHttpClient().send(
                java.net.http.HttpRequest.newBuilder(java.net.URI.create(authorizationUrl)).build(),
                java.net.http.HttpResponse.BodyHandlers.discarding());
        java.net.URI back = java.net.URI.create(page.headers().firstValue("Location").orElseThrow());
        org.assertj.core.api.Assertions.assertThat(back.getPath()).isEqualTo("/api/recorders/plaud/callback");
        mvc.perform(get(back.getPath() + "?" + back.getRawQuery()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"));
        return JsonPath.read(started, "$.signInId");
    }

    private String addSource(String json) throws Exception {
        String body = mvc.perform(post("/api/sources").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private void awaitHubItem(String sourceId, String type, String expectedStatus) {
        awaitHubItem(sourceId, type, expectedStatus, 1);
    }

    private void awaitHubItem(String sourceId, String type, String expectedStatus, long count) {
        await().atMost(Duration.ofSeconds(10)).until(() -> jdbc.sql("""
                        select count(*) from hub_items
                        where source_id = cast(:sourceId as uuid) and item_type = :type and status = :status
                        """)
                .param("sourceId", sourceId).param("type", type).param("status", expectedStatus)
                .query(Long.class).single() == count);
    }
}
