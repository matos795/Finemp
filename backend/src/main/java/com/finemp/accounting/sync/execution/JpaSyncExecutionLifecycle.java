package com.finemp.accounting.sync.execution;

import com.finemp.access.CompanyPermission;
import com.finemp.access.CompanyPermissionAuthorizationService;
import com.finemp.accounting.reading.AccountingReadRequest;
import com.finemp.accounting.source.*;
import com.finemp.accounting.sync.*;
import com.finemp.accounting.sync.control.SyncControlTransactionGuard;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "finemp.sync.executor", name = "enabled", havingValue = "true")
public class JpaSyncExecutionLifecycle implements SyncExecutionLifecycle {
    private final SyncRunRepository runs;
    private final AccountingSourceRepository sources;
    private final SyncRunEventRepository events;
    private final CompanyPermissionAuthorizationService permissions;
    private final SyncControlTransactionGuard transactions;
    private final Clock clock;
    private final SyncAttemptRepository attempts;
    private final SyncRetryPolicy retry;
    public JpaSyncExecutionLifecycle(SyncRunRepository runs, AccountingSourceRepository sources, SyncRunEventRepository events,
            CompanyPermissionAuthorizationService permissions, SyncControlTransactionGuard transactions, Clock clock,
            SyncAttemptRepository attempts, SyncRetryPolicy retry) {
        this.runs = runs; this.sources = sources; this.events = events; this.permissions = permissions;
        this.transactions = transactions; this.clock = clock; this.attempts = attempts; this.retry = retry;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Optional<ClaimedSyncRun> claim(UUID runId) {
        transactions.requireReadCommitted();
        var locked = lock(runId, null);
        if (locked.isEmpty()) return Optional.empty();
        var run = locked.get().run(); var source = locked.get().source(); Instant now = clock.instant();
        if (source.getConnectionMode() != AccountingConnectionMode.DIRECT) return Optional.empty();
        if (run.getStatus() == SyncRunStatus.REQUESTED) {
            run.start(now);
            events.save(new SyncRunEvent(run, SyncRunActorType.SYSTEM, null, now));
        } else if (run.getStatus() == SyncRunStatus.RUNNING) {
            var previous = attempts.findFirstBySyncRunIdOrderByAttemptNumberDesc(runId);
            if (previous.isEmpty()) { reject(run, SyncFailureCode.INVALID_PAYLOAD, now); return Optional.empty(); }
            var attempt = previous.get();
            if (attempt.getStatus() == SyncAttemptStatus.RUNNING) {
                if (now.isBefore(attempt.getLeaseExpiresAt())) return Optional.empty();
                boolean canRetry = retry.canRetry(SyncFailureCode.LEASE_EXPIRED, attempt.getAttemptNumber());
                attempt.expire(now, canRetry ? now.plus(retry.delayAfter(attempt.getAttemptNumber())) : null);
                if (!canRetry) reject(run, SyncFailureCode.RETRY_LIMIT, now);
                return Optional.empty();
            }
            if (attempt.getNextAttemptAt() == null) { reject(run, SyncFailureCode.RETRY_LIMIT, now); return Optional.empty(); }
            if (now.isBefore(attempt.getNextAttemptAt())) return Optional.empty();
            if (run.getAttemptNumber() >= retry.maxAttempts()) { reject(run, SyncFailureCode.RETRY_LIMIT, now); return Optional.empty(); }
        } else return Optional.empty();
        if (!source.isActive() || source.getType() != AccountingSourceType.QUESTOR) {
            reject(run, SyncFailureCode.SOURCE_NOT_AVAILABLE, now); return Optional.empty();
        }
        if (!permissions.hasFreshPermission(run.getRequestedByUserId(), source.getCompany().getId(), CompanyPermission.QUESTOR_SYNC)) {
            reject(run, SyncFailureCode.ACCESS_REVOKED, now); return Optional.empty();
        }
        if (run.getMode() != SyncRunMode.INITIAL) {
            reject(run, SyncFailureCode.UNSUPPORTED_MODE, now); return Optional.empty();
        }
        AccountingReadRequest request;
        try {
            request = new AccountingReadRequest(source.getId(), source.getCompany().getId(), source.getType(),
                    source.getExternalReference(), source.getCompany().getCnpj(), run.getStartDate(), run.getEndDate());
        } catch (IllegalArgumentException invalidContext) {
            reject(run, SyncFailureCode.INVALID_PAYLOAD, now); return Optional.empty();
        }
        int number = run.beginAttempt();
        runs.flush();
        attempts.save(new SyncAttempt(runId, number, run.getVersion(), now, retry.leaseDuration()));
        return Optional.of(new ClaimedSyncRun(run.getId(), run.getVersion(), run.getMode(), request));
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SyncFailureDisposition failIfCurrent(ClaimedSyncRun claim, SyncFailureCode code) {
        transactions.requireReadCommitted();
        var locked = lock(claim.runId(), claim.request().accountingSourceId());
        if (locked.isEmpty()) return SyncFailureDisposition.STALE;
        var run = locked.get().run();
        Instant now = clock.instant();
        var attempt = currentAttempt(run, claim, now);
        if (attempt.isEmpty()) return SyncFailureDisposition.STALE;
        boolean canRetry = retry.canRetry(code, attempt.get().getAttemptNumber());
        attempt.get().fail(code, now, canRetry ? now.plus(retry.delayAfter(attempt.get().getAttemptNumber())) : null);
        if (canRetry) return SyncFailureDisposition.RETRY_SCHEDULED;
        boolean exhausted = code == SyncFailureCode.READ_FAILED || code == SyncFailureCode.PUBLICATION_FAILED;
        reject(run, exhausted && retry.maxAttempts() > 1 ? SyncFailureCode.RETRY_LIMIT : code, now);
        return SyncFailureDisposition.TERMINAL_FAILURE;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean renewLease(ClaimedSyncRun claim) {
        transactions.requireReadCommitted();
        var locked = lock(claim.runId(), claim.request().accountingSourceId());
        if (locked.isEmpty()) return false;
        var source = locked.get().source(); var run = locked.get().run(); Instant now = clock.instant();
        var attempt = currentAttempt(run, claim, now);
        if (attempt.isEmpty() || !source.isActive() || !permissions.hasFreshPermission(run.getRequestedByUserId(),
                source.getCompany().getId(), CompanyPermission.QUESTOR_SYNC)) return false;
        attempt.get().renew(now, retry.leaseDuration());
        return true;
    }

    private Optional<SyncAttempt> currentAttempt(SyncRun run, ClaimedSyncRun claim, Instant now) {
        if (run.getStatus() != SyncRunStatus.RUNNING || run.getVersion() != claim.version() || run.getMode() != claim.mode())
            return Optional.empty();
        return attempts.findBySyncRunIdAndFenceVersion(run.getId(), claim.version()).filter(attempt -> attempt.isLive(now));
    }

    private Optional<LockedRun> lock(UUID runId, UUID expectedSource) {
        var sourceId = runs.findSourceIdByRunId(runId);
        if (sourceId.isEmpty() || (expectedSource != null && !sourceId.get().equals(expectedSource))) return Optional.empty();
        var source = sources.findLockedForSyncById(sourceId.get());
        if (source.isEmpty()) return Optional.empty();
        return runs.findByIdAndAccountingSourceId(runId, sourceId.get()).map(run -> new LockedRun(run, source.get()));
    }
    private void reject(SyncRun run, SyncFailureCode code, Instant now) {
        run.fail(code, now);
        events.save(new SyncRunEvent(run, SyncRunActorType.SYSTEM, null, now));
    }
    private record LockedRun(SyncRun run, AccountingSource source) { }
}

