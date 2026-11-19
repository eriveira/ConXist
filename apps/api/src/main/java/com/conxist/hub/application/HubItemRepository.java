package com.conxist.hub.application;

import java.util.List;
import java.util.UUID;
import com.conxist.hub.domain.HubItem;
import com.conxist.hub.domain.HubItemStatus;
import com.conxist.hub.domain.HubItemType;

public interface HubItemRepository {

    /** Saves the item unless it is already in the hub, so a redelivered event changes nothing. */
    void saveIfAbsent(HubItem item);

    /**
     * Turns the item into the removed marker, or saves the marker when the item has not arrived yet. Later saves of
     * the same item change nothing, so events delivered out of order cannot bring it back.
     */
    void markRemoved(HubItem marker);

    /** Gives a document item its new title, unless it is a removed marker or not there yet. */
    void retitle(UUID workspaceId, UUID sourceId, UUID itemId, String title);

    long count(UUID workspaceId, HubItemType type);

    long countWithStatus(UUID workspaceId, HubItemStatus status);

    List<HubItem> latest(UUID workspaceId, int limit);
}
