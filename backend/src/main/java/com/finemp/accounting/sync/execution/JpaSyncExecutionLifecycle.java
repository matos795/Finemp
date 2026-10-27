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
    public JpaSyncExecutionLifecycle(SyncRunRepository runs, AccountingSourceRepository sources, SyncRunEventRepository events,
            CompanyPermissionAuthorizationService permissions, SyncControlTransactionGuard transactions, Clock clock) {
        this.runs = runs; this.sources = sources; this.events = events; this.permissions = permissions;
        this.transactions = transactions; this.clock = clock;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Optional<ClaimedSyncRun> claim(UUID runId) {
        transactions.requireReadCommitted();
        var locked = lock(runId, null);
        if (locked.isEmpty() || locked.get().run().getStatus() != SyncRunStatus.REQUESTED) return Optional.empty();
        var run = locked.get().run(); var source = locked.get().source(); Instant now = clock.instant();
        run.start(now);
        events.save(new SyncRunEvent(run, SyncRunActorType.SYSTEM, null, now));
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
        runs.flush();
        return Optional.of(new ClaimedSyncRun(run.getId(), run.getVersion(), run.getMode(), request));
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean failIfCurrent(ClaimedSyncRun claim, SyncFailureCode code) {
        transactions.requireReadCommitted();
        var locked = lock(claim.runId(), claim.request().accountingSourceId());
        if (locked.isEmpty()) return false;
        var run = locked.get().run();
        if (run.getStatus() != SyncRunStatus.RUNNING || run.getVersion() != claim.version() || run.getMode() != claim.mode()) return false;
        reject(run, code, clock.instant());
        return true;
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

