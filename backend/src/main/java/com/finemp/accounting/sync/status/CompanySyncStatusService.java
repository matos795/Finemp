package com.finemp.accounting.sync.status;

import com.finemp.access.CompanyPermission;
import com.finemp.access.CompanyPermissionAuthorizationService;
import com.finemp.accounting.source.AccountingSourceRepository;
import com.finemp.accounting.sync.SyncRunRepository;
import com.finemp.accounting.sync.SyncRunStatus;
import com.finemp.accounting.sync.control.SyncRunResponse;
import com.finemp.administration.OrganizationAccessDeniedException;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Leitura company-scoped do estado de sincronização (#440). Exige QUESTOR_SYNC, como a API de SyncRun. */
@Service
public class CompanySyncStatusService {

    private final CompanyPermissionAuthorizationService permissions;
    private final AccountingSourceRepository sources;
    private final SyncRunRepository runs;

    public CompanySyncStatusService(CompanyPermissionAuthorizationService permissions,
                                    AccountingSourceRepository sources, SyncRunRepository runs) {
        this.permissions = permissions;
        this.sources = sources;
        this.runs = runs;
    }

    @Transactional(readOnly = true)
    public CompanySyncStatusResponse findForCompany(UUID actor, UUID companyId) {
        if (!permissions.hasFreshPermission(actor, companyId, CompanyPermission.QUESTOR_SYNC)) {
            throw new OrganizationAccessDeniedException();
        }
        var items = sources.findAllByCompanyId(companyId, Sort.by("type", "id")).stream()
                .map(source -> new CompanySyncStatusResponse.Source(
                        source.getId(),
                        source.getType(),
                        source.getConnectionMode(),
                        source.isActive(),
                        runs.findFirstByAccountingSourceIdOrderByRequestedAtDescIdDesc(source.getId())
                                .map(SyncRunResponse::from).orElse(null),
                        runs.findFirstByAccountingSourceIdAndStatusOrderByRequestedAtDescIdDesc(
                                        source.getId(), SyncRunStatus.SUCCEEDED)
                                .map(SyncRunResponse::from).orElse(null)))
                .toList();
        return new CompanySyncStatusResponse(items);
    }
}
