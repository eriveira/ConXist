package com.conxist.hub.domain;

import java.time.Instant;
import java.util.UUID;

/** Something that has arrived in a workspace's hub. {@code itemId} is the source's or the document's id. */
public record HubItem(UUID itemId, UUID workspaceId, HubItemType type, UUID sourceId, String title,
        HubItemStatus status, Instant arrivedAt) {

    public static HubItem connectedSource(UUID sourceId, UUID workspaceId, String name, Instant addedAt) {
        return new HubItem(sourceId, workspaceId, HubItemType.SOURCE, sourceId, name, HubItemStatus.CONNECTED,
                addedAt);
    }

    /** The marker left by a deleted document: no title, so the hub keeps nothing of it but its ids. */
    public static HubItem removedDocument(UUID documentId, UUID workspaceId, UUID sourceId, Instant deletedAt) {
        return new HubItem(documentId, workspaceId, HubItemType.DOCUMENT, sourceId, "", HubItemStatus.REMOVED,
                deletedAt);
    }

    public static HubItem documentToReview(UUID documentId, UUID workspaceId, UUID sourceId, String title,
            Instant capturedAt) {
        return new HubItem(documentId, workspaceId, HubItemType.DOCUMENT, sourceId, title,
                HubItemStatus.PENDING_REVIEW, capturedAt);
    }
}
