package com.finemp.finance.entry;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialEntrySeriesRepository extends JpaRepository<FinancialEntrySeries,UUID> {
    Optional<FinancialEntrySeries> findByIdAndCompanyId(UUID id,UUID companyId);
}
