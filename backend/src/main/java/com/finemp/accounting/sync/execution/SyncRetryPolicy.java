package com.finemp.accounting.sync.execution;

import com.finemp.accounting.sync.SyncFailureCode;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SyncRetryPolicy {
    private final int maxAttempts;
    private final long leaseSeconds, initialSeconds, maxSeconds;
    public SyncRetryPolicy(@Value("${finemp.sync.retry.max-attempts:3}") int attempts,
            @Value("${finemp.sync.retry.lease-seconds:300}") long lease,
            @Value("${finemp.sync.retry.initial-delay-seconds:30}") long initialDelay,
            @Value("${finemp.sync.retry.max-delay-seconds:300}") long maxDelay) {
        if (attempts < 1 || attempts > 10 || lease < 5 || lease > 3600 || initialDelay < 1
                || maxDelay < initialDelay || maxDelay > 3600) throw new IllegalArgumentException("Invalid sync retry policy.");
        maxAttempts = attempts; leaseSeconds = lease; initialSeconds = initialDelay; maxSeconds = maxDelay;
    }
    public int maxAttempts() { return maxAttempts; }
    public boolean canRetry(SyncFailureCode code, int attempt) {
        return attempt >= 1 && attempt < maxAttempts && (code == SyncFailureCode.READ_FAILED
                || code == SyncFailureCode.PUBLICATION_FAILED || code == SyncFailureCode.LEASE_EXPIRED);
    }
    public Duration leaseDuration() { return Duration.ofSeconds(leaseSeconds); }
    public Duration delayAfter(int attempt) {
        if (attempt < 1 || attempt > 10) throw new IllegalArgumentException("Invalid sync attempt number.");
        return Duration.ofSeconds(Math.min(maxSeconds, initialSeconds * (1L << (attempt - 1))));
    }
}
