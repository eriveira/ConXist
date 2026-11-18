package com.conxist.capture.domain;

import java.time.Duration;
import java.time.Instant;

/** How far back the first import of a meeting recorder reaches. Later recordings always come in. */
public enum StartFrom {
    LAST_30_DAYS(Duration.ofDays(30)),
    LAST_7_DAYS(Duration.ofDays(7)),
    NEW_ONLY(Duration.ZERO);

    private final Duration lookBack;

    StartFrom(Duration lookBack) {
        this.lookBack = lookBack;
    }

    /** Recordings made before this instant are not imported. */
    public Instant cutoff(Instant connectedAt) {
        return connectedAt.minus(lookBack);
    }
}
