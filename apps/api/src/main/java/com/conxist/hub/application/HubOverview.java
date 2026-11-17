package com.conxist.hub.application;

import java.util.List;
import com.conxist.hub.domain.HubItem;

/** What the hub node on the canvas shows. */
public record HubOverview(long sources, long documents, long pendingReview, List<HubItem> latest) {
}
