package com.finemp.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class FixedWindowAttemptLimiterTest {

    @Test
    void shouldRejectExcessAttemptsAndReleaseTheWindowAtTheExactBoundary() {
        MutableClock clock = new MutableClock();
        var limiter = new FixedWindowAttemptLimiter(clock, 10);
        assertThat(limiter.tryAcquire("synthetic", 2, Duration.ofMinutes(1))).isTrue();
        assertThat(limiter.tryAcquire("synthetic", 2, Duration.ofMinutes(1))).isTrue();
        assertThat(limiter.tryAcquire("synthetic", 2, Duration.ofMinutes(1))).isFalse();
        clock.now = clock.now.plusSeconds(60);
        assertThat(limiter.tryAcquire("synthetic", 2, Duration.ofMinutes(1))).isTrue();
    }

    @Test
    void shouldBoundMemoryAndNotEvictAnActiveLimitToAdmitNewKeys() {
        MutableClock clock = new MutableClock();
        var limiter = new FixedWindowAttemptLimiter(clock, 1);
        assertThat(limiter.tryAcquire("first", 1, Duration.ofMinutes(1))).isTrue();
        assertThat(limiter.tryAcquire("second", 1, Duration.ofMinutes(1))).isFalse();
        assertThat(limiter.tryAcquire("first", 1, Duration.ofMinutes(1))).isFalse();
        clock.now = clock.now.plusSeconds(60);
        assertThat(limiter.tryAcquire("second", 1, Duration.ofMinutes(1))).isTrue();
    }

    @Test
    void shouldAdmitExactlyTheLimitUnderConcurrentRequests() throws Exception {
        var limiter = new FixedWindowAttemptLimiter(Clock.systemUTC(), 10);
        try (var executor = Executors.newFixedThreadPool(8)) {
            var calls = java.util.stream.IntStream.range(0, 100)
                    .<java.util.concurrent.Callable<Boolean>>mapToObj(i ->
                            () -> limiter.tryAcquire("shared", 7, Duration.ofMinutes(1)))
                    .toList();
            long admitted = 0;
            for (var result : executor.invokeAll(calls)) {
                if (result.get()) admitted++;
            }
            assertThat(admitted).isEqualTo(7);
        }
    }

    private static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
}
