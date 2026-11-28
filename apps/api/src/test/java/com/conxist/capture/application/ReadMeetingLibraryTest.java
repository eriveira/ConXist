package com.conxist.capture.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import com.conxist.capture.DocumentOrigin;
import com.conxist.capture.domain.CapturedDocument;
import com.conxist.capture.domain.DocumentTitle;
import com.conxist.sources.Provider;
import com.conxist.testing.FixedWorkspace;
import org.junit.jupiter.api.Test;

/** What agents' tools read (ADR 0023): every meeting of the current workspace, and nothing of another. */
class ReadMeetingLibraryTest {

    private final CaptureFakes.Sources sources = new CaptureFakes.Sources();
    private final CaptureFakes.Documents documents = new CaptureFakes.Documents();
    private final ReadMeetingLibrary library = new ReadMeetingLibrary(sources, documents, FixedWorkspace.standard());

    @Test
    void a_meeting_of_this_workspace_is_listed_and_read() {
        UUID sourceId = sources.add(Provider.UPLOAD, null).id();
        CapturedDocument mine = document(FixedWorkspace.WORKSPACE_ID, sourceId, "Ana: guest checkout.");
        documents.saveIfNew(mine);

        assertThat(library.list(sourceId)).singleElement().satisfies(entry -> assertThat(entry.id()).isEqualTo(mine.id()));
        assertThat(library.read(sourceId, mine.id())).hasValueSatisfying(text ->
                assertThat(text.text()).isEqualTo("Ana: guest checkout."));
    }

    @Test
    void a_source_or_meeting_of_another_workspace_reads_as_empty() {
        UUID theirSource = UUID.randomUUID();
        CapturedDocument theirs = document(UUID.randomUUID(), theirSource, "Secret board talk.");
        documents.saveIfNew(theirs);
        assertThat(library.list(theirSource)).isEmpty();
        assertThat(library.read(theirSource, theirs.id())).isEmpty();

        // A source id this workspace knows, holding a row of another workspace, still reads nothing of it.
        UUID shared = sources.add(Provider.UPLOAD, null).id();
        CapturedDocument foreign = document(UUID.randomUUID(), shared, "Secret pricing talk.");
        documents.saveIfNew(foreign);
        assertThat(library.list(shared)).isEmpty();
        assertThat(library.read(shared, foreign.id())).isEmpty();
    }

    private static CapturedDocument document(UUID workspaceId, UUID sourceId, String text) {
        return new CapturedDocument(UUID.randomUUID(), workspaceId, sourceId, DocumentTitle.from("notes.md"),
                "text/markdown", text.length(), text, DocumentOrigin.UPLOAD, UUID.randomUUID().toString(),
                Instant.EPOCH);
    }
}
