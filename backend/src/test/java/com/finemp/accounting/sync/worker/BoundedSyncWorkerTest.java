package com.finemp.accounting.sync.worker;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.finemp.accounting.reading.AccountingReadRequest;
import com.finemp.accounting.source.AccountingSourceType;
import com.finemp.accounting.sync.*;
import com.finemp.accounting.sync.execution.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

class BoundedSyncWorkerTest {
    private final SyncCandidateSource candidates = mock(SyncCandidateSource.class);
    private final SyncExecutionLifecycle lifecycle = mock(SyncExecutionLifecycle.class);
    private final SyncRunExecutor executor = mock(SyncRunExecutor.class);
    private final ExecutorService pool = mock(ExecutorService.class);
    private final ScheduledExecutorService pulses = mock(ScheduledExecutorService.class);
    private final ScheduledFuture<?> future = mock(ScheduledFuture.class);
    private final AtomicLong nanos = new AtomicLong();
    private final UUID first = UUID.randomUUID(), second = UUID.randomUUID();
    private final ClaimedSyncRun claim = new ClaimedSyncRun(first, 1, SyncRunMode.INITIAL,
            new AccountingReadRequest(UUID.randomUUID(), UUID.randomUUID(), AccountingSourceType.QUESTOR,
                    "synthetic", "43434343000143", LocalDate.of(2026,7,1), LocalDate.of(2026,7,31)));
    private BoundedSyncWorker worker;
    @BeforeEach void prepare() {
        worker = new BoundedSyncWorker(candidates, lifecycle, executor, pool, pulses, 1,
                Duration.ofSeconds(1), Duration.ofSeconds(10), nanos::get);
        when(candidates.due(1)).thenReturn(List.of(first, second)); // Untrusted port returns too many: still cap.
        when(lifecycle.claim(first)).thenReturn(Optional.of(claim));
        when(lifecycle.renewLease(claim)).thenReturn(true);
        doReturn(future).when(pulses).scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));
    }
    @AfterEach void close() { worker.close(); }
    @Test void shouldBoundOutstandingWorkWithoutUnboundedQueueOrDuplicateDispatch() {
        worker.poll(); worker.poll();
        var task = ArgumentCaptor.forClass(Runnable.class);
        verify(pool).execute(task.capture()); verify(candidates).due(1);
        task.getValue().run();
        verify(lifecycle).claim(first); verify(executor).executeClaim(claim); verify(future).cancel(false);
        worker.poll(); verify(pool, times(2)).execute(any());
    }
    @Test void shouldReleaseCapacityAfterPoolRejectionAndNeverClaimBeforeTheWorkerStarts() {
        doThrow(new RejectedExecutionException()).when(pool).execute(any());
        worker.poll(); worker.poll();
        verify(pool, times(2)).execute(any()); verifyNoInteractions(lifecycle, executor);
    }
    @Test void shouldCancelHeartbeatWhenTheAttemptLosesItsLeaseAndNeverExtendItAgain() {
        when(lifecycle.renewLease(claim)).thenReturn(false);
        doAnswer(call -> {
            var heartbeat = ArgumentCaptor.forClass(Runnable.class);
            verify(pulses).scheduleAtFixedRate(heartbeat.capture(), eq(1000L), eq(1000L), eq(TimeUnit.MILLISECONDS));
            heartbeat.getValue().run(); heartbeat.getValue().run();
            verify(lifecycle).renewLease(claim);
            return SyncExecutionOutcome.STALE;
        }).when(executor).executeClaim(claim);
        runOne(); verify(future, atLeastOnce()).cancel(false);
    }
    @Test void shouldFenceTimedOutIoThroughSanitizedFailureWithoutInterruptingAReusedWorkerThread() {
        doAnswer(call -> {
            var heartbeat = ArgumentCaptor.forClass(Runnable.class);
            verify(pulses).scheduleAtFixedRate(heartbeat.capture(), anyLong(), anyLong(), any(TimeUnit.class));
            nanos.set(Duration.ofSeconds(10).toNanos());
            heartbeat.getValue().run(); heartbeat.getValue().run();
            verify(lifecycle).failIfCurrent(claim, SyncFailureCode.READ_FAILED);
            verify(lifecycle, never()).renewLease(any());
            assertThat(Thread.currentThread().isInterrupted()).isFalse();
            return SyncExecutionOutcome.STALE;
        }).when(executor).executeClaim(claim);
        runOne();
    }
    @Test void shouldStopPollingAndQueuedWorkOnIdempotentShutdown() {
        worker.poll(); var task = ArgumentCaptor.forClass(Runnable.class); verify(pool).execute(task.capture());
        worker.close(); worker.close(); worker.poll(); task.getValue().run();
        verify(pool).shutdownNow(); verify(pulses).shutdownNow(); verifyNoInteractions(lifecycle, executor);
    }
    @Test void shouldKeepThePollLoopAliveAfterLookupOrHeartbeatSchedulingFailure() {
        when(candidates.due(1)).thenThrow(new IllegalStateException("synthetic private endpoint")).thenReturn(List.of(first));
        worker.poll(); worker.poll();
        var task = ArgumentCaptor.forClass(Runnable.class); verify(pool).execute(task.capture());
        doThrow(new RejectedExecutionException()).when(pulses).scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));
        task.getValue().run(); verifyNoInteractions(executor);
        worker.poll(); verify(pool, times(2)).execute(any());
    }
    @Test void shouldRenewCurrentWorkOnlyWhileTheReadIsRunning() {
        doAnswer(call -> {
            var heartbeat = ArgumentCaptor.forClass(Runnable.class);
            verify(pulses).scheduleAtFixedRate(heartbeat.capture(), anyLong(), anyLong(), any(TimeUnit.class));
            heartbeat.getValue().run();
            verify(lifecycle).renewLease(claim);
            return SyncExecutionOutcome.PUBLISHED;
        }).when(executor).executeClaim(claim);
        runOne(); verify(future).cancel(false);
    }
    private void runOne() { worker.poll(); var task = ArgumentCaptor.forClass(Runnable.class); verify(pool).execute(task.capture()); task.getValue().run(); }
}
