package com.conxist.hub.domain;

/** Sources are connected; documents wait for a person to review them before they reach memory. */
public enum HubItemStatus {
    CONNECTED,
    PENDING_REVIEW,
    /** A document a person deleted: kept as a marker with no title, never counted or shown. */
    REMOVED
}
