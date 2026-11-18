package com.silentecho.silentecho_backend.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class LoginRateLimiterTest {

    /** A clock whose time the test controls. */
    static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-15T10:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void locksOutAfterMaxFailuresAndUnlocksAfterLockout() {
        MutableClock clock = new MutableClock();
        LoginRateLimiter limiter = new LoginRateLimiter(5, Duration.ofMinutes(15), Duration.ofMinutes(15), clock);
        for (int i = 0; i < 4; i++) {
            limiter.recordFailure("203.0.113.9");
        }
        assertFalse(limiter.isBlocked("203.0.113.9"));
        limiter.recordFailure("203.0.113.9");
        assertTrue(limiter.isBlocked("203.0.113.9"));
        clock.advance(Duration.ofMinutes(16));
        assertFalse(limiter.isBlocked("203.0.113.9"));
    }

    @Test
    void successClearsTheCounter() {
        MutableClock clock = new MutableClock();
        LoginRateLimiter limiter = new LoginRateLimiter(3, Duration.ofMinutes(15), Duration.ofMinutes(15), clock);
        limiter.recordFailure("k");
        limiter.recordFailure("k");
        limiter.recordSuccess("k");
        limiter.recordFailure("k");
        limiter.recordFailure("k");
        assertFalse(limiter.isBlocked("k"));
    }

    @Test
    void failuresOutsideTheWindowDoNotAccumulate() {
        MutableClock clock = new MutableClock();
        LoginRateLimiter limiter = new LoginRateLimiter(3, Duration.ofMinutes(10), Duration.ofMinutes(10), clock);
        limiter.recordFailure("k");
        limiter.recordFailure("k");
        clock.advance(Duration.ofMinutes(11));
        limiter.recordFailure("k");
        limiter.recordFailure("k");
        assertFalse(limiter.isBlocked("k"));
    }

    @Test
    void keysAreIsolated() {
        MutableClock clock = new MutableClock();
        LoginRateLimiter limiter = new LoginRateLimiter(1, Duration.ofMinutes(5), Duration.ofMinutes(5), clock);
        limiter.recordFailure("a");
        assertTrue(limiter.isBlocked("a"));
        assertFalse(limiter.isBlocked("b"));
    }
}
