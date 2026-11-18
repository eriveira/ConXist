package com.conxist.testing;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

public final class TestClock {

    public static final Instant NOW = Instant.parse("2026-11-17T12:00:00Z");

    private TestClock() {
    }

    public static Clock fixed() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }
}
