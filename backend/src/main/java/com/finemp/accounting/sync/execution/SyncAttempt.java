package com.finemp.accounting.sync.execution;

import com.finemp.accounting.sync.SyncFailureCode;
import jakarta.persistence.*;
import java.time.*;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "sync_attempts")
public class SyncAttempt {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "sync_run_id", nullable = false, updatable = false) private UUID syncRunId;
    @Column(name = "attempt_number", nullable = false, updatable = false) private int attemptNumber;
    @Column(name = "fence_version", nullable = false, updatable = false) private long fenceVersion;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private SyncAttemptStatus status;
    @Column(name = "started_at", nullable = false, updatable = false) private Instant startedAt;
    @Column(name = "last_renewed_at", nullable = false) private Instant lastRenewedAt;
    @Column(name = "lease_expires_at", nullable = false) private Instant leaseExpiresAt;
    @Column(name = "finished_at") private Instant finishedAt;
    @Column(name = "next_attempt_at") private Instant nextAttemptAt;
    @Enumerated(EnumType.STRING) @Column(name = "failure_code", length = 40) private SyncFailureCode failureCode;
    protected SyncAttempt() { }
    public SyncAttempt(UUID runId, int number, long version, Instant now, Duration lease) {
        if (number < 1 || number > 10 || version < 0 || lease == null || lease.isNegative() || lease.isZero())
            throw new IllegalArgumentException("Invalid sync attempt.");
        syncRunId = Objects.requireNonNull(runId); attemptNumber = number; fenceVersion = version;
        startedAt = Objects.requireNonNull(now); lastRenewedAt = now; leaseExpiresAt = now.plus(lease); status = SyncAttemptStatus.RUNNING;
    }
    public boolean isLive(Instant now) {
        return status == SyncAttemptStatus.RUNNING && now != null && !now.isBefore(lastRenewedAt) && now.isBefore(leaseExpiresAt);
    }
    public void renew(Instant now, Duration lease) {
        requireLive(now);
        if (lease == null || lease.isNegative() || lease.isZero()) throw new IllegalArgumentException("Invalid sync lease.");
        lastRenewedAt = now; leaseExpiresAt = now.plus(lease);
    }
    public void fail(SyncFailureCode code, Instant now, Instant next) {
        requireLive(now); finish(SyncAttemptStatus.FAILED, Objects.requireNonNull(code), now, next);
    }
    public void expire(Instant now, Instant next) {
        if (status != SyncAttemptStatus.RUNNING || now == null || now.isBefore(leaseExpiresAt))
            throw new IllegalStateException("Sync lease has not expired.");
        finish(SyncAttemptStatus.EXPIRED, SyncFailureCode.LEASE_EXPIRED, now, next);
    }
    public void succeed(Instant now) { requireLive(now); finish(SyncAttemptStatus.SUCCEEDED, null, now, null); }
    private void finish(SyncAttemptStatus state, SyncFailureCode code, Instant now, Instant next) {
        if (next != null && next.isBefore(now)) throw new IllegalArgumentException("Invalid retry time.");
        status = state; failureCode = code; finishedAt = now; nextAttemptAt = next;
    }
    private void requireLive(Instant now) { if (!isLive(now)) throw new IllegalStateException("Sync attempt is not live."); }
    public UUID getId() { return id; }
    public UUID getSyncRunId() { return syncRunId; }
    public int getAttemptNumber() { return attemptNumber; }
    public long getFenceVersion() { return fenceVersion; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getLastRenewedAt() { return lastRenewedAt; }
    public Instant getLeaseExpiresAt() { return leaseExpiresAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public SyncFailureCode getFailureCode() { return failureCode; }
    public SyncAttemptStatus getStatus() { return status; }
    @Override public String toString() { return "SyncAttempt[number=" + attemptNumber + ", status=" + status + "]"; }
}
