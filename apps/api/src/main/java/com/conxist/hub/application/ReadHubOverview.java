package com.conxist.hub.application;

import java.util.UUID;
import com.conxist.hub.domain.HubItemStatus;
import com.conxist.hub.domain.HubItemType;
import com.conxist.platform.CurrentWorkspace;

public final class ReadHubOverview {

    static final int LATEST_ITEMS = 20;

    private final HubItemRepository items;
    private final CurrentWorkspace workspace;

    public ReadHubOverview(HubItemRepository items, CurrentWorkspace workspace) {
        this.items = items;
        this.workspace = workspace;
    }

    public HubOverview read() {
        UUID workspaceId = workspace.id();
        return new HubOverview(
                items.count(workspaceId, HubItemType.SOURCE),
                items.count(workspaceId, HubItemType.DOCUMENT),
                items.countWithStatus(workspaceId, HubItemStatus.PENDING_REVIEW),
                items.latest(workspaceId, LATEST_ITEMS));
    }
}
