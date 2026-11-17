package com.conxist.capture.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import com.conxist.capture.DocumentOrigin;
import com.conxist.capture.domain.CapturedDocument;
import com.conxist.capture.domain.DocumentTitle;
import com.conxist.testing.FixedWorkspace;
import org.junit.jupiter.api.Test;

class ListDocumentsTest {

    private final CaptureFakes.Documents documents = new CaptureFakes.Documents();
    private final UUID sourceId = UUID.randomUUID();

    @Test
    void lists_a_sources_documents_without_their_text() {
        documents.saveIfNew(document(FixedWorkspace.WORKSPACE_ID, "Every source connects to the hub."));

        assertThat(new ListDocuments(documents, FixedWorkspace.standard()).list(sourceId)).singleElement()
                .satisfies(summary -> {
                    assertThat(summary.title()).isEqualTo("notes.md");
                    assertThat(summary.textLength()).isEqualTo(33);
                });
    }

    @Test
    void lists_nothing_from_another_workspace() {
        documents.saveIfNew(document(UUID.randomUUID(), "Private notes"));

        assertThat(new ListDocuments(documents, FixedWorkspace.standard()).list(sourceId)).isEmpty();
    }

    private CapturedDocument document(UUID workspaceId, String text) {
        return new CapturedDocument(UUID.randomUUID(), workspaceId, sourceId, DocumentTitle.from("notes.md"),
                "text/markdown", text.length(), text, DocumentOrigin.UPLOAD, UUID.randomUUID().toString(),
                Instant.EPOCH);
    }
}
