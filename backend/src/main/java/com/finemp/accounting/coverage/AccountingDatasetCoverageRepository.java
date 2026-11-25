package com.finemp.accounting.coverage;

import com.finemp.accounting.canonical.v2.AccountingDataset;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountingDatasetCoverageRepository
        extends JpaRepository<AccountingDatasetCoverage, UUID> {

    List<AccountingDatasetCoverage>
            findAllByAccountingSourceIdAndDatasetOrderByImportedFromAscImportedToAsc(
                    UUID accountingSourceId,
                    AccountingDataset dataset);
}
