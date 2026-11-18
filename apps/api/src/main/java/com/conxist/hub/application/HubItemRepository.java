package com.conxist.hub.application;

import java.util.List;
import java.util.UUID;
import com.conxist.hub.domain.HubItem;
import com.conxist.hub.domain.HubItemStatus;
import com.conxist.hub.domain.HubItemType;

public interface HubItemRepository {

    /** Saves the item unless it is already in the hub, so a redelivered event changes nothing. */
    void saveIfAbsent(HubItem item);

    long count(UUID workspaceId, HubItemType type);

    long countWithStatus(UUID workspaceId, HubItemStatus status);

    List<HubItem> latest(UUID workspaceId, int limit);
}
