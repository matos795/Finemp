package com.finemp.accounting.sync.worker;

import com.finemp.accounting.sync.SyncFailureCode;
import com.finemp.accounting.sync.execution.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.LongSupplier;

/** Local capacity is bounded; database claims/fences arbitrate between instances. */
public class BoundedSyncWorker implements AutoCloseable {
    private final SyncCandidateSource candidates;
    private final SyncExecutionLifecycle lifecycle;
    private final SyncRunExecutor executor;
    private final ExecutorService workers;
    private final ScheduledExecutorService heartbeats;
    private final Semaphore capacity;
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean polling = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final long heartbeatMillis, maxExecutionNanos;
    private final LongSupplier nanoTime;
    private final LongAdder failures = new LongAdder(), rejections = new LongAdder(), timeouts = new LongAdder();

    public BoundedSyncWorker(SyncCandidateSource candidates, SyncExecutionLifecycle lifecycle, SyncRunExecutor executor,
            ExecutorService workers, ScheduledExecutorService heartbeats, int concurrency,
            Duration heartbeatInterval, Duration maxExecutionTime, LongSupplier nanoTime) {
        if (concurrency < 1 || concurrency > 8 || heartbeatInterval == null || heartbeatInterval.toMillis() < 1000
                || maxExecutionTime == null || maxExecutionTime.compareTo(heartbeatInterval.multipliedBy(2)) < 0
                || maxExecutionTime.compareTo(Duration.ofHours(1)) > 0) throw new IllegalArgumentException("Invalid sync worker limits.");
        this.candidates = Objects.requireNonNull(candidates); this.lifecycle = Objects.requireNonNull(lifecycle);
        this.executor = Objects.requireNonNull(executor); this.workers = Objects.requireNonNull(workers);
        this.heartbeats = Objects.requireNonNull(heartbeats); capacity = new Semaphore(concurrency);
        heartbeatMillis = heartbeatInterval.toMillis(); maxExecutionNanos = maxExecutionTime.toNanos(); this.nanoTime = Objects.requireNonNull(nanoTime);
    }
    public void poll() {
        if (closed.get() || !polling.compareAndSet(false, true)) return;
        try {
            int available = capacity.availablePermits();
            if (available == 0) return;
            var ids = candidates.due(available, Set.copyOf(inFlight));
            // Do not trust an implementation of the port to respect the requested resource cap.
            for (var id : ids.stream().limit(available).toList()) {
                if (closed.get()) break;
                if (!inFlight.add(id)) continue;
                if (!capacity.tryAcquire()) { inFlight.remove(id); break; }
                try { workers.execute(() -> process(id)); }
                catch (RejectedExecutionException rejected) { rejections.increment(); release(id); }
            }
        } catch (RuntimeException unavailable) {
            failures.increment();
            // A future tick retries lookup; never log free text, credentials, SQL or accounting payload.
        } finally { polling.set(false); }
    }
    private void process(UUID id) {
        ScheduledFuture<?> heartbeat = null;
        try {
            if (closed.get()) return;
            var claim = lifecycle.claim(id);
            if (claim.isEmpty()) return;
            long started = nanoTime.getAsLong();
            var active = new AtomicBoolean(true);
            var future = new AtomicReference<ScheduledFuture<?>>();
            heartbeat = heartbeats.scheduleAtFixedRate(() -> {
                if (!active.get()) return;
                try {
                    if (closed.get()) { active.set(false); }
                    else if (nanoTime.getAsLong() - started >= maxExecutionNanos) {
                        active.set(false);
                        timeouts.increment();
                        lifecycle.failIfCurrent(claim.get(), SyncFailureCode.READ_FAILED);
                    } else if (!lifecycle.renewLease(claim.get())) active.set(false);
                } catch (RuntimeException unavailable) { failures.increment(); active.set(false); }
                if (!active.get() && future.get() != null) future.get().cancel(false);
            }, heartbeatMillis, heartbeatMillis, TimeUnit.MILLISECONDS);
            future.set(heartbeat);
            executor.executeClaim(claim.get());
        } catch (RuntimeException unavailable) {
            failures.increment();
            // Failed claim/heartbeat scheduling leaves no unbounded loop: the lease is recoverable.
        } finally {
            if (heartbeat != null) heartbeat.cancel(false);
            release(id);
        }
    }
    private void release(UUID id) { inFlight.remove(id); capacity.release(); }
    public WorkerStatistics statistics() { return new WorkerStatistics(failures.sum(), rejections.sum(), timeouts.sum()); }
    public record WorkerStatistics(long infrastructureFailures, long capacityRejections, long executionTimeouts) { }
    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        heartbeats.shutdownNow();
        workers.shutdownNow();
    }
}
