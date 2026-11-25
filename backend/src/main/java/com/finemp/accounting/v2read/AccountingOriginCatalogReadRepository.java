package com.finemp.accounting.v2read;

import com.finemp.accounting.canonical.persistence.AccountingOriginCatalogEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

interface AccountingOriginCatalogReadRepository extends Repository<AccountingOriginCatalogEntry, UUID> {
    List<AccountingOriginCatalogEntry> findAllByAccountingSourceIdOrderByOriginCodeAsc(UUID accountingSourceId);
}
