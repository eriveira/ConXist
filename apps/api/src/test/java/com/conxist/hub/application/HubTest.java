package com.conxist.hub.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.conxist.capture.DocumentCaptured;
import com.conxist.capture.DocumentOrigin;
import com.conxist.hub.domain.HubItem;
import com.conxist.hub.domain.HubItemStatus;
import com.conxist.hub.domain.HubItemType;
import com.conxist.sources.Provider;
import com.conxist.sources.SourceAdded;
import com.conxist.testing.FixedWorkspace;
import com.conxist.testing.TestClock;
import org.junit.jupiter.api.Test;

class HubTest {

    private final InMemoryHubItems items = new InMemoryHubItems();
    private final RecordArrival recordArrival = new RecordArrival(items);
    private final ReadHubOverview readHubOverview = new ReadHubOverview(items, FixedWorkspace.standard());
    private final UUID sourceId = UUID.randomUUID();

    @Test
    void a_connected_source_counts_as_a_source() {
        recordArrival.sourceAdded(new SourceAdded(sourceId, FixedWorkspace.WORKSPACE_ID, Provider.UPLOAD, "Specs",
                TestClock.NOW));

        HubOverview overview = readHubOverview.read();

        assertThat(overview.sources()).isOne();
        assertThat(overview.pendingReview()).isZero();
        assertThat(overview.latest()).singleElement()
                .satisfies(item -> assertThat(item.status()).isEqualTo(HubItemStatus.CONNECTED));
    }

    @Test
    void a_captured_document_waits_for_review() {
        recordArrival.documentCaptured(document(UUID.randomUUID()));

        HubOverview overview = readHubOverview.read();

        assertThat(overview.documents()).isOne();
        assertThat(overview.pendingReview()).isOne();
    }

    @Test
    void the_same_event_delivered_twice_is_recorded_once() {
        DocumentCaptured event = document(UUID.randomUUID());

        recordArrival.documentCaptured(event);
        recordArrival.documentCaptured(event);

        assertThat(readHubOverview.read().documents()).isOne();
    }

    @Test
    void counts_only_the_current_workspace() {
        recordArrival.sourceAdded(new SourceAdded(sourceId, UUID.randomUUID(), Provider.UPLOAD, "Other", TestClock.NOW));

        assertThat(readHubOverview.read().sources()).isZero();
        assertThat(readHubOverview.read().latest()).isEmpty();
    }

    private DocumentCaptured document(UUID documentId) {
        return new DocumentCaptured(documentId, FixedWorkspace.WORKSPACE_ID, sourceId, "notes.md",
                DocumentOrigin.UPLOAD, TestClock.NOW);
    }

    private static final class InMemoryHubItems implements HubItemRepository {
        private final List<HubItem> saved = new ArrayList<>();

        @Override
        public void saveIfAbsent(HubItem item) {
            if (saved.stream().noneMatch(existing -> existing.itemId().equals(item.itemId()))) {
                saved.add(item);
            }
        }

        @Override
        public long count(UUID workspaceId, HubItemType type) {
            return inWorkspace(workspaceId).stream().filter(item -> item.type() == type).count();
        }

        @Override
        public long countWithStatus(UUID workspaceId, HubItemStatus status) {
            return inWorkspace(workspaceId).stream().filter(item -> item.status() == status).count();
        }

        @Override
        public List<HubItem> latest(UUID workspaceId, int limit) {
            return inWorkspace(workspaceId).stream().limit(limit).toList();
        }

        private List<HubItem> inWorkspace(UUID workspaceId) {
            return saved.stream().filter(item -> item.workspaceId().equals(workspaceId)).toList();
        }
    }
}
