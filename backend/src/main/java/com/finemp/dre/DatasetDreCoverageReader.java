package com.finemp.dre;

import com.finemp.accounting.canonical.v2.AccountingDataset;
import com.finemp.accounting.coverage.AccountingDatasetCoverageService;
import com.finemp.accounting.source.AccountingSourceRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
class DatasetDreCoverageReader implements DreCoverageReader {

    private final AccountingSourceRepository sources;
    private final AccountingDatasetCoverageService coverage;

    DatasetDreCoverageReader(
            AccountingSourceRepository sources,
            AccountingDatasetCoverageService coverage) {
        this.sources = sources;
        this.coverage = coverage;
    }

    @Override
    public List<DreCalculationResult.SourceCoverage> coverage(UUID companyId) {
        return sources.findAllByCompanyId(companyId, Sort.by("type", "id")).stream()
                .map(source -> {
                    var snapshot = coverage.read(source.getId(), AccountingDataset.MOVEMENTS);
                    return new DreCalculationResult.SourceCoverage(
                            source.getId(),
                            snapshot.importedFrom(),
                            snapshot.importedTo(),
                            snapshot.intervals().stream()
                                    .map(interval -> new DreCalculationResult.CoverageInterval(
                                            interval.importedFrom(),
                                            interval.importedTo()))
                                    .toList());
                })
                .toList();
    }
}
