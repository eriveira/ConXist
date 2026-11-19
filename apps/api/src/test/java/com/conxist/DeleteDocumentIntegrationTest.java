package com.conxist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import com.conxist.capture.DocumentOrigin;
import com.conxist.capture.application.CapturedDocumentRepository;
import com.conxist.capture.domain.CapturedDocument;
import com.conxist.capture.domain.DocumentTitle;
import com.conxist.testing.FakePlaudConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/** Deleting a document against a real PostgreSQL: who may, from where, and a check racing the delete. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresContainerConfiguration.class, FakePlaudConfiguration.class, WorkspaceHeaderConfiguration.class})
class DeleteDocumentIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    CapturedDocumentRepository documents;

    @Autowired
    TransactionTemplate transactions;

    @BeforeEach
    void workspace() {
        WorkspaceHeaderConfiguration.ensure(jdbc);
    }

    @Test
    void another_workspaces_document_cannot_be_deleted() throws Exception {
        UUID otherWorkspace = UUID.randomUUID();
        UUID foreignSource = source(otherWorkspace);
        CapturedDocument theirs = meeting(otherWorkspace, foreignSource, "rec-theirs");
        documents.saveIfNew(theirs);

        mvc.perform(delete("/api/sources/{id}/documents/{doc}", foreignSource, theirs.id()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("source.not-found"));

        assertThat(count("select count(*) from captured_documents where id = :id", theirs.id())).isOne();
        assertThat(count("select count(*) from deleted_recordings where source_id = :id", foreignSource)).isZero();
        assertThat(count("select count(*) from hub_items where item_id = :id and status = 'REMOVED'", theirs.id()))
                .isZero();
    }

    @Test
    void a_delete_from_another_site_or_host_is_refused() throws Exception {
        UUID mine = source(WorkspaceHeaderConfiguration.TEST_WORKSPACE);
        CapturedDocument document = meeting(WorkspaceHeaderConfiguration.TEST_WORKSPACE, mine, "rec-csrf");
        documents.saveIfNew(document);

        mvc.perform(delete("/api/sources/{id}/documents/{doc}", mine, document.id())
                .header("Origin", "https://attacker.example")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/sources/{id}/documents/{doc}", mine, document.id())
                .header("Host", "rebound.attacker.example")).andExpect(status().isForbidden());

        assertThat(count("select count(*) from captured_documents where id = :id", document.id())).isOne();
    }

    @Test
    void a_check_saving_a_recording_while_it_is_deleted_does_not_bring_it_back() throws Exception {
        UUID mine = source(WorkspaceHeaderConfiguration.TEST_WORKSPACE);
        CapturedDocument first = meeting(WorkspaceHeaderConfiguration.TEST_WORKSPACE, mine, "rec-race");
        documents.saveIfNew(first);
        CountDownLatch deletedNotCommitted = new CountDownLatch(1);

        CompletableFuture<Void> deleting = CompletableFuture.runAsync(() -> transactions.executeWithoutResult(status -> {
            documents.delete(first, true, Instant.now());
            deletedNotCommitted.countDown();
            sleep(1_000);
        }));
        assertThat(deletedNotCommitted.await(10, TimeUnit.SECONDS)).isTrue();
        Boolean savedAgain = transactions.execute(status ->
                documents.saveIfNew(meeting(WorkspaceHeaderConfiguration.TEST_WORKSPACE, mine, "rec-race")));
        deleting.get(10, TimeUnit.SECONDS);

        assertThat(savedAgain).isFalse();
        assertThat(count("select count(*) from captured_documents where source_id = :id", mine)).isZero();
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
        return new CapturedDocument(UUID.randomUUID(), workspaceId, sourceId, new DocumentTitle("Planning"),
                "text/markdown", 4, "text", DocumentOrigin.RECORDING, reference, Instant.now());
    }

    private long count(String sql, UUID id) {
        return jdbc.sql(sql).param("id", id).query(Long.class).single();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
