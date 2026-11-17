package com.conxist.hub.application;

import java.time.Instant;
import java.util.UUID;
import com.conxist.hub.domain.HubItem;
import com.conxist.hub.domain.HubItemStatus;
import com.conxist.hub.domain.HubItemType;

/** A hub item as the API shows it. */
public record HubItemView(UUID itemId, HubItemType type, UUID sourceId, String title, HubItemStatus status,
        Instant arrivedAt) {

    static HubItemView of(HubItem item) {
        return new HubItemView(item.itemId(), item.type(), item.sourceId(), item.title(), item.status(),
                item.arrivedAt());
    }
}
