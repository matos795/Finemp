package com.finemp.accounting.classification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AccountingAccountClassificationRepository
        extends JpaRepository<AccountingAccountClassification, UUID> {

    Optional<AccountingAccountClassification> findByAccountingAccountId(UUID accountingAccountId);
}