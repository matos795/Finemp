package com.finemp.accounting.sync.execution;

import static org.assertj.core.api.Assertions.*;
import com.finemp.accounting.sync.SyncFailureCode;
import java.time.*;
import org.junit.jupiter.api.Test;

class SyncRetryPolicyTest {
    private final SyncRetryPolicy policy = new SyncRetryPolicy(3, 300, 30, 300);
    @Test void shouldRetryOnlySanitizedTransientFailuresWithinTheBudget() {
        for (var code : SyncFailureCode.values())
            assertThat(policy.canRetry(code, 1)).isEqualTo(code == SyncFailureCode.READ_FAILED
                    || code == SyncFailureCode.PUBLICATION_FAILED || code == SyncFailureCode.LEASE_EXPIRED);
        assertThat(policy.canRetry(SyncFailureCode.READ_FAILED, 3)).isFalse();
    }
    @Test void shouldUseBoundedExponentialBackoffWithoutDependingOnWallClockOrPayload() {
        var larger = new SyncRetryPolicy(10, 300, 30, 100);
        assertThat(larger.delayAfter(1)).isEqualTo(Duration.ofSeconds(30));
        assertThat(larger.delayAfter(2)).isEqualTo(Duration.ofSeconds(60));
        assertThat(larger.delayAfter(10)).isEqualTo(Duration.ofSeconds(100));
    }
    @Test void shouldTreatTheExactLeaseDeadlineAsExpired() {
        var now = Instant.parse("2026-07-01T12:00:00Z");
        var lease = new SyncAttempt(java.util.UUID.randomUUID(), 1, 7, now, policy.leaseDuration());
        assertThat(lease.isLive(now.plusSeconds(299))).isTrue();
        assertThat(lease.isLive(now.plusSeconds(300))).isFalse();
    }
    @Test void shouldRenewWithoutChangingTheFenceAndNeverResurrectAnExpiredAttempt() {
        var now = Instant.parse("2026-07-01T12:00:00Z");
        var attempt = new SyncAttempt(java.util.UUID.randomUUID(), 1, 7, now, policy.leaseDuration());
        attempt.renew(now.plusSeconds(200), policy.leaseDuration());
        assertThat(attempt.getFenceVersion()).isEqualTo(7);
        assertThat(attempt.getLeaseExpiresAt()).isEqualTo(now.plusSeconds(500));
        assertThatThrownBy(() -> attempt.renew(now.plusSeconds(500), policy.leaseDuration())).isInstanceOf(IllegalStateException.class);
    }
    @Test void shouldKeepAttemptFailureSeparateFromTheOverallRunAndNeverAcceptItTwice() {
        var now = Instant.parse("2026-07-01T12:00:00Z");
        var attempt = new SyncAttempt(java.util.UUID.randomUUID(), 1, 7, now, policy.leaseDuration());
        attempt.fail(SyncFailureCode.READ_FAILED, now.plusSeconds(2), now.plusSeconds(32));
        assertThat(attempt.getStatus()).isEqualTo(SyncAttemptStatus.FAILED);
        assertThat(attempt.getNextAttemptAt()).isEqualTo(now.plusSeconds(32));
        assertThat(attempt.isLive(now.plusSeconds(3))).isFalse();
        assertThatThrownBy(() -> attempt.succeed(now.plusSeconds(3))).isInstanceOf(IllegalStateException.class);
    }
    @Test void shouldRejectUnboundedOrInvalidResourceConfigurationAndAttemptNumbers() {
        assertThatThrownBy(() -> new SyncRetryPolicy(11, 300, 30, 300)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SyncRetryPolicy(3, 0, 30, 300)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SyncRetryPolicy(3, 300, 0, 300)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SyncRetryPolicy(3, 300, 300, 30)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.delayAfter(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
