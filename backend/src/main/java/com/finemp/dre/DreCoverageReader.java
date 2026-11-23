package com.finemp.dre;

import java.util.List;
import java.util.UUID;

/** Cobertura factual da base por fonte (última execução SUCCEEDED). */
@FunctionalInterface
public interface DreCoverageReader {
    List<DreCalculationResult.SourceCoverage> coverage(UUID companyId);
}
