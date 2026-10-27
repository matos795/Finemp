package com.finemp.accounting.sync.control;

import com.finemp.access.*;
import com.finemp.accounting.source.AccountingSourceRepository;
import com.finemp.accounting.sync.SyncRunRepository;
import com.finemp.accounting.sync.execution.SyncAttemptRepository;
import com.finemp.administration.*;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "finemp.sync.control", name = "enabled", havingValue = "true")
public class SyncAttemptQueryService {
    private final AccountingSourceRepository sources;
    private final SyncRunRepository runs;
    private final SyncAttemptRepository attempts;
    private final CompanyPermissionAuthorizationService permissions;
    public SyncAttemptQueryService(AccountingSourceRepository sources, SyncRunRepository runs, SyncAttemptRepository attempts,
            CompanyPermissionAuthorizationService permissions) {
        this.sources = sources; this.runs = runs; this.attempts = attempts; this.permissions = permissions;
    }
    @Transactional(readOnly = true)
    public PagedResponse<SyncAttemptResponse> list(UUID actor, UUID sourceId, UUID runId, int page, int size) {
        var company = sources.findCompanyIdById(sourceId).orElseThrow(OrganizationAccessDeniedException::new);
        if (!permissions.hasFreshPermission(actor, company, CompanyPermission.QUESTOR_SYNC)) throw new OrganizationAccessDeniedException();
        runs.findByIdAndAccountingSourceId(runId, sourceId).orElseThrow(() -> new SaasResourceNotFoundException("Sync run"));
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Invalid sync page.");
        return PagedResponse.from(attempts.findAllBySyncRunId(runId, PageRequest.of(page, size,
                Sort.by("attemptNumber").descending().and(Sort.by("id")))), SyncAttemptResponse::from);
    }
}
