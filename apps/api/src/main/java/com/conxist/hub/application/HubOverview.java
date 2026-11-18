package com.conxist.hub.application;

import java.util.List;

/** What the hub node on the canvas shows. */
public record HubOverview(long sources, long documents, long pendingReview, List<HubItemView> latest) {
}
