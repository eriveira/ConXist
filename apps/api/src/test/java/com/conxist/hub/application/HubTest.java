package com.conxist.hub.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.conxist.capture.DocumentCaptured;
import com.conxist.capture.DocumentDeleted;
import com.conxist.capture.DocumentOrigin;
import com.conxist.capture.DocumentRetitled;
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
    void a_deleted_document_leaves_the_hub_and_a_second_delivery_changes_nothing() {
        UUID documentId = UUID.randomUUID();
        recordArrival.documentCaptured(document(documentId));
        DocumentDeleted deleted = new DocumentDeleted(documentId, FixedWorkspace.WORKSPACE_ID, sourceId, TestClock.NOW);

        recordArrival.documentDeleted(deleted);
        recordArrival.documentDeleted(deleted);

        assertThat(readHubOverview.read().documents()).isZero();
        assertThat(readHubOverview.read().pendingReview()).isZero();
    }

    @Test
    void a_capture_event_delivered_after_the_delete_does_not_bring_the_document_back() {
        UUID documentId = UUID.randomUUID();

        recordArrival.documentDeleted(new DocumentDeleted(documentId, FixedWorkspace.WORKSPACE_ID, sourceId, TestClock.NOW));
        recordArrival.documentCaptured(document(documentId));

        assertThat(readHubOverview.read().documents()).isZero();
        assertThat(readHubOverview.read().pendingReview()).isZero();
        assertThat(readHubOverview.read().latest()).isEmpty();
    }

    @Test
    void a_delete_from_another_workspace_removes_nothing() {
        UUID documentId = UUID.randomUUID();
        recordArrival.documentCaptured(document(documentId));

        recordArrival.documentDeleted(new DocumentDeleted(documentId, UUID.randomUUID(), sourceId, TestClock.NOW));

        assertThat(readHubOverview.read().documents()).isOne();
    }

    @Test
    void a_renamed_document_keeps_its_new_name_and_a_deleted_or_foreign_one_is_left_alone() {
        UUID renamed = UUID.randomUUID();
        UUID deleted = UUID.randomUUID();
        recordArrival.documentCaptured(document(renamed));
        recordArrival.documentDeleted(new DocumentDeleted(deleted, FixedWorkspace.WORKSPACE_ID, sourceId, TestClock.NOW));

        recordArrival.documentRetitled(new DocumentRetitled(renamed, UUID.randomUUID(), sourceId, "Theirs", TestClock.NOW));
        recordArrival.documentRetitled(new DocumentRetitled(renamed, FixedWorkspace.WORKSPACE_ID, sourceId, "Sprint 42",
                TestClock.NOW));
        recordArrival.documentRetitled(new DocumentRetitled(deleted, FixedWorkspace.WORKSPACE_ID, sourceId, "Back?",
                TestClock.NOW));

        assertThat(readHubOverview.read().latest()).singleElement()
                .satisfies(item -> assertThat(item.title()).isEqualTo("Sprint 42"));
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
        public void markRemoved(HubItem marker) {
            boolean otherWorkspace = saved.stream().anyMatch(item -> item.itemId().equals(marker.itemId())
                    && !item.workspaceId().equals(marker.workspaceId()));
            if (!otherWorkspace) {
                saved.removeIf(item -> item.itemId().equals(marker.itemId()));
                saved.add(marker);
            }
        }

        @Override
        public void retitle(UUID workspaceId, UUID sourceId, UUID itemId, String title) {
            saved.replaceAll(item -> item.itemId().equals(itemId) && item.workspaceId().equals(workspaceId)
                    && item.sourceId().equals(sourceId) && item.type() == HubItemType.DOCUMENT
                    && item.status() != HubItemStatus.REMOVED
                    ? new HubItem(item.itemId(), item.workspaceId(), item.type(), item.sourceId(), title, item.status(),
                            item.arrivedAt())
                    : item);
        }

        @Override
        public long count(UUID workspaceId, HubItemType type) {
            return shown(workspaceId).stream().filter(item -> item.type() == type).count();
        }

        @Override
        public long countWithStatus(UUID workspaceId, HubItemStatus status) {
            return inWorkspace(workspaceId).stream().filter(item -> item.status() == status).count();
        }

        @Override
        public List<HubItem> latest(UUID workspaceId, int limit) {
            return shown(workspaceId).stream().limit(limit).toList();
        }

        private List<HubItem> shown(UUID workspaceId) {
            return inWorkspace(workspaceId).stream().filter(item -> item.status() != HubItemStatus.REMOVED).toList();
        }

        private List<HubItem> inWorkspace(UUID workspaceId) {
            return saved.stream().filter(item -> item.workspaceId().equals(workspaceId)).toList();
        }
    }
}
