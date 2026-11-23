package com.finemp.accounting.classification;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AccountingAccountClassificationQueryService {

    private final AccountingAccountClassificationQueryRepository queryRepository;

    public AccountingAccountClassificationQueryService(
            AccountingAccountClassificationQueryRepository queryRepository) {
        this.queryRepository = queryRepository;
    }

    @Transactional(readOnly = true)
    public List<AccountingAccountClassificationResponse> findAllByCompanyId(
            UUID companyId) {

        return queryRepository
                .findAllByCompanyId(companyId)
                .stream()
                .map(AccountingAccountClassificationResponse::from)
                .toList();
    }

    /** Página e summary são consultas distintas: REPEATABLE_READ garante o mesmo snapshot. */
    @Transactional(readOnly = true,
            isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public ImportedAccountsPageResponse searchImported(
            UUID companyId,
            UUID accountingSourceId,
            String search,
            ImportedAccountStatusFilter status,
            int page,
            int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Invalid accounts page.");
        }
        boolean allSources = accountingSourceId == null;
        UUID sourceId = allSources ? new UUID(0, 0) : accountingSourceId;
        String normalizedSearch = search == null ? "" : search.strip().toLowerCase(java.util.Locale.ROOT);
        String statusName = status == null ? "ALL" : status.name();

        var result = queryRepository.searchImported(companyId, allSources, sourceId, normalizedSearch, statusName,
                org.springframework.data.domain.PageRequest.of(page, size));
        var summary = queryRepository.summarizeImported(companyId, allSources, sourceId, normalizedSearch);

        return new ImportedAccountsPageResponse(
                ImportedAccountsPageResponse.IMPORTED_ACCOUNTS,
                result.getContent().stream().map(AccountingAccountClassificationResponse::from).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                summary);
    }
}
