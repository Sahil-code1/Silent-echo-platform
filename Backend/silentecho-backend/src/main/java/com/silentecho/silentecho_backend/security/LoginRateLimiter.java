package com.silentecho.silentecho_backend.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory throttle for failed sign-in attempts, keyed by client address.
 *
 * <p>After {@code maxFailures} failures inside {@code window}, the key is locked out for
 * {@code lockout}. A successful sign-in clears the key. This state lives in one JVM only.
 * A multi-instance deployment needs a shared store such as Redis.
 */
public final class LoginRateLimiter {

    private static final int PRUNE_THRESHOLD = 10_000;

    private final int maxFailures;
    private final Duration window;
    private final Duration lockout;
    private final Clock clock;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    private record Entry(int failures, Instant windowStart, Instant lockedUntil) {
    }

    public LoginRateLimiter(int maxFailures, Duration window, Duration lockout, Clock clock) {
        if (maxFailures < 1) {
            throw new IllegalArgumentException("maxFailures must be at least 1");
        }
        this.maxFailures = maxFailures;
        this.window = window;
        this.lockout = lockout;
        this.clock = clock;
    }

    public synchronized boolean isBlocked(String key) {
        Entry entry = entries.get(key);
        return entry != null && entry.lockedUntil() != null && clock.instant().isBefore(entry.lockedUntil());
    }

    public synchronized void recordFailure(String key) {
        Instant now = clock.instant();
        Entry current = entries.get(key);
        if (current == null || now.isAfter(current.windowStart().plus(window))) {
            current = new Entry(0, now, null);
        }
        int failures = current.failures() + 1;
        if (failures >= maxFailures) {
            entries.put(key, new Entry(0, now, now.plus(lockout)));
        } else {
            entries.put(key, new Entry(failures, current.windowStart(), null));
        }
        prune(now);
    }

    public synchronized void recordSuccess(String key) {
        entries.remove(key);
    }

    private void prune(Instant now) {
        if (entries.size() <= PRUNE_THRESHOLD) {
            return;
        }
        entries.entrySet().removeIf(e -> e.getValue().windowStart().plus(window).isBefore(now)
                && (e.getValue().lockedUntil() == null || e.getValue().lockedUntil().isBefore(now)));
    }
}
