package com.finemp.accounting.sync;

import com.finemp.accounting.source.AccountingSource;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "sync_runs")
public class SyncRun {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "accounting_source_id", nullable = false, updatable = false)
    private AccountingSource accountingSource;
    @Column(name = "requested_by_user_id", nullable = false, updatable = false) private UUID requestedByUserId;
    @Column(name = "idempotency_key", nullable = false, updatable = false) private UUID idempotencyKey;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30, updatable = false) private SyncRunMode mode;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private SyncRunStatus status;
    @Column(name = "start_date", nullable = false, updatable = false) private LocalDate startDate;
    @Column(name = "end_date", nullable = false, updatable = false) private LocalDate endDate;
    @Column(name = "requested_at", nullable = false, updatable = false) private Instant requestedAt;
    @Column(name = "started_at") private Instant startedAt;
    @Column(name = "finished_at") private Instant finishedAt;
    @Column(name = "read_count") private Long readCount;
    @Column(name = "imported_count") private Long importedCount;
    @Column(name = "skipped_count") private Long skippedCount;
    @Enumerated(EnumType.STRING) @Column(name = "failure_code", length = 40) private SyncFailureCode failureCode;
    @Version private long version;

    protected SyncRun() { }
    public SyncRun(AccountingSource source, UUID actor, UUID key, SyncRunMode mode, LocalDate start, LocalDate end, Instant now) {
        accountingSource = Objects.requireNonNull(source);
        requestedByUserId = Objects.requireNonNull(actor);
        idempotencyKey = Objects.requireNonNull(key);
        this.mode = Objects.requireNonNull(mode);
        if (start == null || end == null || start.isAfter(end)) throw new IllegalArgumentException("Invalid sync period.");
        startDate = start; endDate = end; requestedAt = Objects.requireNonNull(now); status = SyncRunStatus.REQUESTED;
    }

    public void start(Instant now) {
        requireStatus(SyncRunStatus.REQUESTED);
        requireAtOrAfter(now, requestedAt);
        startedAt = now; status = SyncRunStatus.RUNNING;
    }
    public void succeed(long read, long imported, long skipped, Instant now) {
        requireStatus(SyncRunStatus.RUNNING);
        requireAtOrAfter(now, startedAt);
        if (read < 0 || imported < 0 || skipped < 0 || java.math.BigInteger.valueOf(imported)
                .add(java.math.BigInteger.valueOf(skipped)).compareTo(java.math.BigInteger.valueOf(read)) != 0)
            throw new IllegalArgumentException("Invalid sync counters.");
        readCount = read; importedCount = imported; skippedCount = skipped;
        finishedAt = now; status = SyncRunStatus.SUCCEEDED;
    }
    public void fail(SyncFailureCode code, Instant now) {
        requireStatus(SyncRunStatus.RUNNING);
        requireAtOrAfter(now, startedAt);
        failureCode = Objects.requireNonNull(code); finishedAt = now; status = SyncRunStatus.FAILED;
    }
    public void cancel(Instant now) {
        if (status == SyncRunStatus.CANCELLED) return;
        requireStatus(SyncRunStatus.REQUESTED);
        requireAtOrAfter(now, requestedAt);
        finishedAt = now; status = SyncRunStatus.CANCELLED;
    }
    public boolean matchesRequest(SyncRunMode mode, LocalDate start, LocalDate end) {
        return this.mode == mode && startDate.equals(start) && endDate.equals(end);
    }
    private void requireStatus(SyncRunStatus expected) {
        if (status != expected) throw new IllegalStateException("Invalid sync transition.");
    }
    private void requireAtOrAfter(Instant now, Instant previous) {
        if (now == null || now.isBefore(previous)) throw new IllegalArgumentException("Invalid sync timestamp.");
    }
    public UUID getId() { return id; }
    public AccountingSource getAccountingSource() { return accountingSource; }
    public UUID getRequestedByUserId() { return requestedByUserId; }
    public UUID getIdempotencyKey() { return idempotencyKey; }
    public SyncRunMode getMode() { return mode; }
    public SyncRunStatus getStatus() { return status; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public Long getReadCount() { return readCount; }
    public Long getImportedCount() { return importedCount; }
    public Long getSkippedCount() { return skippedCount; }
    public SyncFailureCode getFailureCode() { return failureCode; }
}
