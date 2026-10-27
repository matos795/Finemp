package com.finemp.accounting.sync.control;

import com.finemp.access.CompanyPermission;
import com.finemp.access.CompanyPermissionAuthorizationService;
import com.finemp.accounting.source.*;
import com.finemp.accounting.sync.*;
import com.finemp.administration.*;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "finemp.sync.control", name = "enabled", havingValue = "true")
public class SyncControlService {
    private final AccountingSourceRepository sources;
    private final SyncRunRepository runs;
    private final SyncRunEventRepository events;
    private final CompanyPermissionAuthorizationService permissions;
    private final Clock clock;
    private final int maxWindowDays;
    private final SyncControlTransactionGuard transactionGuard;
    public SyncControlService(AccountingSourceRepository sources, SyncRunRepository runs, SyncRunEventRepository events,
            CompanyPermissionAuthorizationService permissions, Clock clock,
            @Value("${finemp.sync.control.max-window-days:366}") int maxWindowDays,
            SyncControlTransactionGuard transactionGuard) {
        if (maxWindowDays < 1) throw new IllegalArgumentException("Invalid sync window configuration.");
        this.sources = sources; this.runs = runs; this.events = events; this.permissions = permissions;
        this.clock = clock; this.maxWindowDays = maxWindowDays;
        this.transactionGuard = transactionGuard;
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SyncRunResponse request(UUID actor, UUID sourceId, UUID key, RequestSyncRun request) {
        transactionGuard.requireReadCommitted();
        requireAccess(actor, sourceId);
        if (key == null || request == null) throw new IllegalArgumentException("Invalid sync request.");
        var source = sources.findLockedForSyncById(sourceId).orElseThrow(() -> new SaasResourceNotFoundException("Accounting source"));
        requireAccess(actor, sourceId);
        if (request.mode() == null || request.startDate() == null || request.endDate() == null
                || request.startDate().isAfter(request.endDate()))
            throw new IllegalArgumentException("Invalid sync period.");
        var existing = runs.findByAccountingSourceIdAndIdempotencyKey(sourceId, key);
        if (existing.isPresent()) {
            if (!existing.get().matchesRequest(request.mode(), request.startDate(), request.endDate()))
                throw new SyncControlConflictException(SyncControlConflictException.Reason.IDEMPOTENCY_MISMATCH);
            return SyncRunResponse.from(existing.get());
        }
        if (ChronoUnit.DAYS.between(request.startDate(), request.endDate()) + 1 > maxWindowDays)
            throw new IllegalArgumentException("Sync window exceeds resource limit.");
        if (!source.isActive() || source.getType() != AccountingSourceType.QUESTOR)
            throw new SyncControlConflictException(SyncControlConflictException.Reason.SOURCE_NOT_AVAILABLE);
        if (runs.existsByAccountingSourceIdAndStatusIn(sourceId, Set.of(SyncRunStatus.REQUESTED, SyncRunStatus.RUNNING)))
            throw new SyncControlConflictException(SyncControlConflictException.Reason.ACTIVE_RUN_EXISTS);
        Instant now = clock.instant();
        var run = runs.save(new SyncRun(source, actor, key, request.mode(), request.startDate(), request.endDate(), now));
        events.save(new SyncRunEvent(run, SyncRunActorType.USER, actor, now));
        return SyncRunResponse.from(run);
    }
    @Transactional(readOnly = true)
    public PagedResponse<SyncRunResponse> list(UUID actor, UUID sourceId, SyncRunStatus status, int page, int size) {
        requireAccess(actor, sourceId);
        var paging = paging(page, size, "requestedAt");
        var result = status == null ? runs.findAllByAccountingSourceId(sourceId, paging)
                : runs.findAllByAccountingSourceIdAndStatus(sourceId, status, paging);
        return PagedResponse.from(result, SyncRunResponse::from);
    }
    @Transactional(readOnly = true)
    public SyncRunResponse find(UUID actor, UUID sourceId, UUID runId) {
        requireAccess(actor, sourceId); return SyncRunResponse.from(requireRun(sourceId, runId));
    }
    @Transactional(readOnly = true)
    public PagedResponse<SyncRunEventResponse> events(UUID actor, UUID sourceId, UUID runId, int page, int size) {
        requireAccess(actor, sourceId); requireRun(sourceId, runId);
        return PagedResponse.from(events.findAllBySyncRunIdAndSyncRunAccountingSourceId(runId, sourceId,
                paging(page, size, "transitionIndex")), SyncRunEventResponse::from);
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void cancel(UUID actor, UUID sourceId, UUID runId) {
        transactionGuard.requireReadCommitted();
        requireAccess(actor, sourceId);
        sources.findLockedForSyncById(sourceId).orElseThrow(() -> new SaasResourceNotFoundException("Accounting source"));
        requireAccess(actor, sourceId);
        var run = requireRun(sourceId, runId);
        if (run.getStatus() == SyncRunStatus.CANCELLED) return;
        if (run.getStatus() != SyncRunStatus.REQUESTED)
            throw new SyncControlConflictException(SyncControlConflictException.Reason.RUN_NOT_CANCELABLE);
        Instant now = clock.instant(); run.cancel(now);
        events.save(new SyncRunEvent(run, SyncRunActorType.USER, actor, now));
    }
    private SyncRun requireRun(UUID sourceId, UUID runId) {
        return runs.findByIdAndAccountingSourceId(runId, sourceId).orElseThrow(() -> new SaasResourceNotFoundException("Sync run"));
    }
    private void requireAccess(UUID actor, UUID sourceId) {
        var companyId = sources.findCompanyIdById(sourceId).orElseThrow(OrganizationAccessDeniedException::new);
        if (!permissions.hasFreshPermission(actor, companyId, CompanyPermission.QUESTOR_SYNC)) throw new OrganizationAccessDeniedException();
    }
    private PageRequest paging(int page, int size, String timestamp) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Invalid sync page.");
        return PageRequest.of(page, size, Sort.by(timestamp).descending().and(Sort.by("id")));
    }
}

