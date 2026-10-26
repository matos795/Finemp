package com.finemp.accounting.classification;

import org.springframework.data.domain.PageRequest;
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

    @Transactional(readOnly = true)
    public AccountingAccountClassificationPageResponse findPageByCompanyId(
            UUID companyId,
            int page,
            int size,
            AccountingAccountClassificationStatus status,
            String search) {

        String normalizedSearch = search == null || search.isBlank()
                ? null
                : search.trim();

        String statusValue = status == null
                ? null
                : status.name();

        var result = queryRepository.findPageByCompanyId(
                companyId,
                normalizedSearch,
                statusValue,
                PageRequest.of(page, size));

        long totalAccounts = queryRepository.countByCompanyId(
                companyId);

        long classifiedAccounts = queryRepository.countClassifiedByCompanyId(
                companyId);

        long pendingAccounts = totalAccounts - classifiedAccounts;

        return new AccountingAccountClassificationPageResponse(
                result.getContent()
                        .stream()
                        .map(AccountingAccountClassificationResponse::from)
                        .toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                totalAccounts,
                pendingAccounts,
                classifiedAccounts);
    }
}