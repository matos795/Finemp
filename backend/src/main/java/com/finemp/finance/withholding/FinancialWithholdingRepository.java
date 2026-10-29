package com.finemp.finance.withholding;

import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialWithholdingRepository extends JpaRepository<FinancialWithholding,UUID> {
    Optional<FinancialWithholding> findByIdAndCompanyId(UUID id,UUID companyId);
    Page<FinancialWithholding> findByCompanyIdAndEntryId(UUID companyId,UUID entryId,Pageable pageable);
}
