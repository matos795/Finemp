package com.finemp.dre;

import java.util.List;
import java.util.UUID;

/** Cobertura factual cumulativa da base importada por fonte. */
@FunctionalInterface
public interface DreCoverageReader {
    List<DreCalculationResult.SourceCoverage> coverage(UUID companyId);
}
