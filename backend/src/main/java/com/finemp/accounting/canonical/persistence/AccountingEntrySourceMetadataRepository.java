package com.finemp.accounting.canonical.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountingEntrySourceMetadataRepository
        extends JpaRepository<AccountingEntrySourceMetadata, UUID> {
    Optional<AccountingEntrySourceMetadata> findByAccountingSourceIdAndSourceEntryKey(
            UUID accountingSourceId,
            String sourceEntryKey);
}
