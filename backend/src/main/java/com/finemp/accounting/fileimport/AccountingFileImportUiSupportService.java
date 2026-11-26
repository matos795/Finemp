package com.finemp.accounting.fileimport;

import com.finemp.access.CompanyPermission;
import com.finemp.access.CompanyPermissionAuthorizationService;
import com.finemp.accounting.source.AccountingSource;
import com.finemp.accounting.source.AccountingSourceRepository;
import com.finemp.accounting.source.AccountingSourceType;
import com.finemp.administration.OrganizationAccessDeniedException;
import com.finemp.company.CompanyRepository;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountingFileImportUiSupportService {
    private final CompanyRepository companies;
    private final AccountingSourceRepository sources;
    private final AccountingFileImportReceiptRepository receipts;
    private final CompanyPermissionAuthorizationService permissions;

    public AccountingFileImportUiSupportService(
            CompanyRepository companies,
            AccountingSourceRepository sources,
            AccountingFileImportReceiptRepository receipts,
            CompanyPermissionAuthorizationService permissions) {
        this.companies = companies;
        this.sources = sources;
        this.receipts = receipts;
        this.permissions = permissions;
    }

    @Transactional
    public AccountingFileImportSourceResponse prepareSource(UUID actorId, UUID companyId) {
        requirePermission(actorId, companyId);

        var existing = sources.findAllByCompanyId(companyId, org.springframework.data.domain.Sort.by("id")).stream()
                .filter(source -> source.getType() == AccountingSourceType.FILE_IMPORT && source.isActive())
                .findFirst();
        if (existing.isPresent()) {
            return AccountingFileImportSourceResponse.from(existing.get());
        }

        var company = companies.findById(companyId)
                .orElseThrow(OrganizationAccessDeniedException::new);
        return AccountingFileImportSourceResponse.from(
                sources.save(new AccountingSource(company, AccountingSourceType.FILE_IMPORT, null, true)));
    }

    @Transactional(readOnly = true)
    public List<AccountingFileImportHistoryItemResponse> history(UUID actorId, UUID accountingSourceId) {
        var source = sources.findForReadingById(accountingSourceId)
                .filter(candidate -> candidate.getType() == AccountingSourceType.FILE_IMPORT)
                .orElseThrow(OrganizationAccessDeniedException::new);
        requirePermission(actorId, source.getCompany().getId());

        return receipts.findAllBySource(accountingSourceId).stream()
                .sorted(Comparator.comparing(AccountingFileImportReceipt::committedAt).reversed())
                .map(AccountingFileImportHistoryItemResponse::from)
                .toList();
    }

    private void requirePermission(UUID actorId, UUID companyId) {
        if (actorId == null
                || companyId == null
                || !permissions.hasFreshPermission(
                        actorId,
                        companyId,
                        CompanyPermission.ACCOUNTING_IMPORT_MANAGE)) {
            throw new OrganizationAccessDeniedException();
        }
    }
}
