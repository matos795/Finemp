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
}