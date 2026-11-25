package com.finemp.accounting.fileimport;

import com.finemp.access.CompanyPermission;
import com.finemp.access.CompanyPermissionAuthorizationService;
import com.finemp.accounting.source.AccountingSourceRepository;
import com.finemp.accounting.source.AccountingSourceType;
import com.finemp.administration.OrganizationAccessDeniedException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountingFileImportFoundationService {
    private final AccountingSourceRepository sources;
    private final CompanyPermissionAuthorizationService permissions;
    private final GeneralLedgerLayoutProfileRegistry layouts;

    public AccountingFileImportFoundationService(
            AccountingSourceRepository sources,
            CompanyPermissionAuthorizationService permissions,
            GeneralLedgerLayoutProfileRegistry layouts) {
        this.sources = sources;
        this.permissions = permissions;
        this.layouts = layouts;
    }

    @Transactional(readOnly = true)
    public GeneralLedgerLayoutProfile requireLayout(
            UUID actorId,
            UUID accountingSourceId,
            String profileId,
            AccountingFileFormat format) {
        if (actorId == null || accountingSourceId == null) {
            throw new OrganizationAccessDeniedException();
        }

        var source = sources.findForReadingById(accountingSourceId)
                .orElseThrow(() -> new AccountingFileImportConflictException(
                        AccountingFileImportConflictException.Reason.SOURCE_NOT_AVAILABLE));

        if (source.getType() != AccountingSourceType.FILE_IMPORT || !source.isActive()) {
            throw new AccountingFileImportConflictException(
                    AccountingFileImportConflictException.Reason.SOURCE_NOT_AVAILABLE);
        }

        if (!permissions.hasFreshPermission(
                actorId,
                source.getCompany().getId(),
                CompanyPermission.ACCOUNTING_IMPORT_MANAGE)) {
            throw new OrganizationAccessDeniedException();
        }

        return layouts.require(profileId, format);
    }
}
