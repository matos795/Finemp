package com.finemp.accounting.canonical.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountingOriginCatalogEntryRepository
        extends JpaRepository<AccountingOriginCatalogEntry, UUID> {
    Optional<AccountingOriginCatalogEntry> findByAccountingSourceIdAndOriginCode(
            UUID accountingSourceId,
            String originCode);
}
