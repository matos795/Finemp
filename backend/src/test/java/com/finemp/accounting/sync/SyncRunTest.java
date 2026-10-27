package com.finemp.accounting.sync;

import static org.assertj.core.api.Assertions.*;
import com.finemp.accounting.source.*;
import com.finemp.company.Company;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SyncRunTest {
    private final Instant now = Instant.parse("2026-07-01T12:00:00Z");
    private final LocalDate start = LocalDate.of(2026, 7, 1), end = LocalDate.of(2026, 7, 31);

    @Test void shouldCompleteOnlyAfterStartWithConsistentMeasuredCounters() {
        var run = run();
        assertThat(run.getStatus()).isEqualTo(SyncRunStatus.REQUESTED);
        assertThat(run.getReadCount()).isNull();
        assertThatThrownBy(() -> run.succeed(3, 2, 1, now)).isInstanceOf(IllegalStateException.class);
        run.start(now);
        run.succeed(3, 2, 1, now.plusSeconds(1));
        assertThat(run.getStatus()).isEqualTo(SyncRunStatus.SUCCEEDED);
        assertThat(run.getReadCount()).isEqualTo(3);
        assertThat(run.getImportedCount()).isEqualTo(2);
        assertThat(run.getSkippedCount()).isEqualTo(1);
        assertThatThrownBy(() -> run.start(now)).isInstanceOf(IllegalStateException.class);
    }
    @Test void shouldRejectNegativeMismatchedAndOverflowingCountersWithoutChangingState() {
        var run = run(); run.start(now);
        assertThatThrownBy(() -> run.succeed(-1, 0, 0, now)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> run.succeed(3, 1, 1, now)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> run.succeed(Long.MAX_VALUE, Long.MAX_VALUE, 1, now)).isInstanceOf(IllegalArgumentException.class);
        assertThat(run.getStatus()).isEqualTo(SyncRunStatus.RUNNING);
        assertThat(run.getReadCount()).isNull();
    }
    @Test void shouldRecordSanitizedFailureWithoutInventingMeasuredCounters() {
        var run = run(); run.start(now); run.fail(SyncFailureCode.READ_FAILED, now);
        assertThat(run.getStatus()).isEqualTo(SyncRunStatus.FAILED);
        assertThat(run.getFailureCode()).isEqualTo(SyncFailureCode.READ_FAILED);
        assertThat(run.getReadCount()).isNull();
        assertThatThrownBy(() -> run.succeed(0, 0, 0, now)).isInstanceOf(IllegalStateException.class);
    }
    @Test void shouldCancelOnlyQueuedWorkAndKeepCancellationIdempotent() {
        var run = run(); run.cancel(now); run.cancel(now);
        assertThat(run.getStatus()).isEqualTo(SyncRunStatus.CANCELLED);
        assertThat(run.getStartedAt()).isNull();
        var running = run(); running.start(now);
        assertThatThrownBy(() -> running.cancel(now)).isInstanceOf(IllegalStateException.class);
    }
    @Test void shouldRejectTimeTravelAndCompareIdempotentRequestSemantics() {
        var run = run();
        assertThatThrownBy(() -> run.start(now.minusSeconds(1))).isInstanceOf(IllegalArgumentException.class);
        assertThat(run.matchesRequest(SyncRunMode.INITIAL, start, end)).isTrue();
        assertThat(run.matchesRequest(SyncRunMode.RECONCILIATION, start, end)).isFalse();
        run.start(now);
        assertThatThrownBy(() -> run.fail(SyncFailureCode.READ_FAILED, now.minusSeconds(1))).isInstanceOf(IllegalArgumentException.class);
    }
    private SyncRun run() {
        return new SyncRun(new AccountingSource(new Company("Synthetic", "20202020000120"), AccountingSourceType.QUESTOR, "synthetic", true),
                UUID.randomUUID(), UUID.randomUUID(), SyncRunMode.INITIAL, start, end, now);
    }
}
