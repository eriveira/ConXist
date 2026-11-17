package com.conxist.hub.application;

import com.conxist.capture.DocumentCaptured;
import com.conxist.hub.domain.HubItem;
import com.conxist.sources.SourceAdded;

/** Records in the hub each source that connects and each document captured. */
public final class RecordArrival {

    private final HubItemRepository items;

    public RecordArrival(HubItemRepository items) {
        this.items = items;
    }

    public void sourceAdded(SourceAdded event) {
        items.saveIfAbsent(HubItem.connectedSource(event.sourceId(), event.workspaceId(), event.name(),
                event.addedAt()));
    }

    public void documentCaptured(DocumentCaptured event) {
        items.saveIfAbsent(HubItem.documentToReview(event.documentId(), event.workspaceId(), event.sourceId(),
                event.title(), event.capturedAt()));
    }
}
