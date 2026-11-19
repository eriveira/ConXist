package com.conxist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.conxist.capture.DocumentOrigin;
import com.conxist.capture.application.CapturedDocumentRepository;
import com.conxist.capture.domain.CapturedDocument;
import com.conxist.capture.domain.DocumentTitle;
import com.conxist.capture.domain.MeetingText;
import com.conxist.capture.domain.MeetingText.Line;
import com.conxist.testing.FakePlaudConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/** Editing and restoring a meeting against a real PostgreSQL: the original kept sealed, stale edits, workspaces. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresContainerConfiguration.class, FakePlaudConfiguration.class, WorkspaceHeaderConfiguration.class})
class EditMeetingIntegrationTest {

    private static final String SECRET_LINE = "The launch date is March 3.";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    CapturedDocumentRepository documents;

    @BeforeEach
    void workspace() {
        WorkspaceHeaderConfiguration.ensure(jdbc);
    }

    @Test
    void an_edit_keeps_the_original_sealed_and_restore_puts_it_back() throws Exception {
        UUID source = source(WorkspaceHeaderConfiguration.TEST_WORKSPACE);
        CapturedDocument meeting = meeting(WorkspaceHeaderConfiguration.TEST_WORKSPACE, source, "rec-edit");
        documents.saveIfNew(meeting);
        jdbc.sql("""
                insert into hub_items (item_id, workspace_id, item_type, source_id, title, status, arrived_at)
                values (:id, :workspaceId, 'DOCUMENT', :sourceId, 'Planning', 'PENDING_REVIEW', now())
                """).param("id", meeting.id()).param("workspaceId", WorkspaceHeaderConfiguration.TEST_WORKSPACE)
                .param("sourceId", source).update();

        String body = mvc.perform(put("/api/sources/{s}/documents/{d}/meeting", source, meeting.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"editedAt": null, "title": "Launch review", "speakers": {"Speaker 1": "Ana"},
                                 "lines": {"0": "The launch date is March 10."}}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.title").value("Launch review"))
                .andExpect(jsonPath("$.meeting.lines[0].speaker").value("Ana"))
                .andExpect(jsonPath("$.meeting.lines[0].text").value("The launch date is March 10."))
                .andReturn().getResponse().getContentAsString();
        String firstEdit = editedAtOf(body);
        CapturedDocument afterFirst = documents.find(WorkspaceHeaderConfiguration.TEST_WORKSPACE, source, meeting.id())
                .orElseThrow();
        assertThat(hubTitleSoon(meeting.id(), "Launch review")).isEqualTo("Launch review");

        // A second edit starts from the first and keeps what was imported as the original.
        String editedAt = editedAtOf(mvc.perform(put("/api/sources/{s}/documents/{d}/meeting", source, meeting.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"editedAt\": \"" + firstEdit + "\", \"title\": \"Launch review 2\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(jdbc.sql("select original_title from captured_documents where id = :id").param("id", meeting.id())
                .query(String.class).single()).isEqualTo("Planning");
        assertThat(documents.saveEdit(afterFirst, new DocumentTitle("Stale"), afterFirst.text(), Instant.now())).isFalse();

        byte[] original = jdbc.sql("select original_sealed from captured_documents where id = :id")
                .param("id", meeting.id()).query(byte[].class).single();
        assertThat(new String(original, StandardCharsets.ISO_8859_1)).doesNotContain(SECRET_LINE);
        mvc.perform(put("/api/sources/{s}/documents/{d}/meeting", source, meeting.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"editedAt\": null, \"title\": \"From an old tab\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("document.changed"));

        mvc.perform(post("/api/sources/{s}/documents/{d}/restore", source, meeting.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"editedAt\": \"" + editedAt + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Planning"))
                .andExpect(jsonPath("$.text").value(meeting.text()))
                .andExpect(jsonPath("$.editedAt").doesNotExist())
                .andExpect(jsonPath("$.meeting.lines[0].text").value(SECRET_LINE));
        assertThat(jdbc.sql("select count(*) from captured_documents where id = :id and original_sealed is null")
                .param("id", meeting.id()).query(Long.class).single()).isOne();
    }

    @Test
    void another_workspaces_meeting_cannot_be_edited() throws Exception {
        UUID other = UUID.randomUUID();
        UUID foreignSource = source(other);
        CapturedDocument theirs = meeting(other, foreignSource, "rec-edit-theirs");
        documents.saveIfNew(theirs);

        mvc.perform(put("/api/sources/{s}/documents/{d}/meeting", foreignSource, theirs.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"editedAt\": null, \"title\": \"Mine now\"}"))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/sources/{s}/documents/{d}/restore", foreignSource, theirs.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"editedAt\": \"2026-11-19T12:00:00Z\"}"))
                .andExpect(status().isNotFound());
        assertThat(jdbc.sql("select title from captured_documents where id = :id").param("id", theirs.id())
                .query(String.class).single()).isEqualTo("Planning");
    }

    @Test
    void a_body_larger_than_any_edit_is_refused_before_it_is_read() throws Exception {
        UUID source = source(WorkspaceHeaderConfiguration.TEST_WORKSPACE);
        mvc.perform(put("/api/sources/{s}/documents/{d}/meeting", source, UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"" + "x".repeat(5 * 1024 * 1024) + "\"}"))
                .andExpect(status().isPayloadTooLarge());
    }

    private static String editedAtOf(String body) {
        return body.replaceAll(".*\"editedAt\":\"([^\"]+)\".*", "$1");
    }

    /** The hub hears of a new title after the edit commits, on its own thread. */
    private String hubTitleSoon(UUID id, String expected) throws InterruptedException {
        String title = null;
        for (int attempt = 0; attempt < 50 && !expected.equals(title); attempt++) {
            Thread.sleep(100);
            title = jdbc.sql("select title from hub_items where item_id = :id").param("id", id).query(String.class).single();
        }
        return title;
    }

    private UUID source(UUID workspaceId) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                insert into sources (id, workspace_id, provider, name, added_at)
                values (:id, :workspaceId, 'PLAUD', :name, now())
                """).param("id", id).param("workspaceId", workspaceId).param("name", "Meetings " + id).update();
        return id;
    }

    private static CapturedDocument meeting(UUID workspaceId, UUID sourceId, String reference) {
        String text = MeetingText.compose("Planning", Instant.parse("2026-11-16T15:04:00Z"), "en", null, List.of(),
                List.of(new Line("Speaker 1", 0, SECRET_LINE), new Line("Speaker 2", 4_000, "Agreed.")), 10_000);
        return new CapturedDocument(UUID.randomUUID(), workspaceId, sourceId, new DocumentTitle("Planning"),
                MeetingText.MEDIA_TYPE, text.length(), text, DocumentOrigin.RECORDING, reference, Instant.now());
    }
}
