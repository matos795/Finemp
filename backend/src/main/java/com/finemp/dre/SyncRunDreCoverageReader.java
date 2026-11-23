package com.finemp.dre;

import com.finemp.accounting.source.AccountingSourceRepository;
import com.finemp.accounting.sync.SyncRunRepository;
import com.finemp.accounting.sync.SyncRunStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
class SyncRunDreCoverageReader implements DreCoverageReader {

    private final AccountingSourceRepository sources;
    private final SyncRunRepository runs;

    SyncRunDreCoverageReader(AccountingSourceRepository sources, SyncRunRepository runs) {
        this.sources = sources;
        this.runs = runs;
    }

    @Override
    public List<DreCalculationResult.SourceCoverage> coverage(UUID companyId) {
        return sources.findAllByCompanyId(companyId, Sort.by("type", "id")).stream()
                .map(source -> runs.findFirstByAccountingSourceIdAndStatusOrderByRequestedAtDescIdDesc(
                                source.getId(), SyncRunStatus.SUCCEEDED)
                        .map(run -> new DreCalculationResult.SourceCoverage(source.getId(), run.getStartDate(), run.getEndDate()))
                        .orElse(new DreCalculationResult.SourceCoverage(source.getId(), null, null)))
                .toList();
    }
}
