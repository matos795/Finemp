package com.finemp.identity;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** Bounded, process-local limiter. Keys supplied by the guard contain only hashes. */
public class FixedWindowAttemptLimiter {

    private final Clock clock;
    private final int capacity;
    private final Map<String, Window> windows = new HashMap<>();

    public FixedWindowAttemptLimiter(Clock clock, int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("Limiter capacity must be positive.");
        this.clock = clock;
        this.capacity = capacity;
    }

    public synchronized boolean tryAcquire(String key, int limit, Duration duration) {
        if (limit < 1 || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("Attempt limits and windows must be positive.");
        }
        Instant now = clock.instant();
        Window current = windows.get(key);
        if (current != null && !now.isBefore(current.expiresAt)) {
            windows.remove(key);
            current = null;
        }
        if (current == null) {
            if (windows.size() >= capacity) {
                windows.values().removeIf(window -> !now.isBefore(window.expiresAt));
                if (windows.size() >= capacity) return false;
            }
            windows.put(key, new Window(now.plus(duration), 1));
            return true;
        }
        if (current.attempts >= limit) return false;
        current.attempts++;
        return true;
    }

    private static final class Window {
        private final Instant expiresAt;
        private int attempts;

        private Window(Instant expiresAt, int attempts) {
            this.expiresAt = expiresAt;
            this.attempts = attempts;
        }
    }
}
