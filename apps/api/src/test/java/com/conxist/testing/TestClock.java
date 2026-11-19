package com.conxist.testing;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

public final class TestClock {

    public static final Instant NOW = Instant.parse("2026-11-17T12:00:00Z");

    private TestClock() {
    }

    public static Clock fixed() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    /** Starts at {@link #NOW} and moves only when a test says so. */
    public static Moving moving() {
        return new Moving();
    }

    public static final class Moving extends Clock {
        private Instant now = NOW;

        public void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
