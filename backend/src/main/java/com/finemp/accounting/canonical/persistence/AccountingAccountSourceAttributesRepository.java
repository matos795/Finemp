package com.finemp.accounting.canonical.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountingAccountSourceAttributesRepository
        extends JpaRepository<AccountingAccountSourceAttributes, UUID> {
    Optional<AccountingAccountSourceAttributes> findByAccountingSourceIdAndSourceAccountId(
            UUID accountingSourceId,
            String sourceAccountId);
}
