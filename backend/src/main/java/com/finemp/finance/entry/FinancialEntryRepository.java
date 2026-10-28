package com.finemp.finance.entry;

import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface FinancialEntryRepository extends JpaRepository<FinancialEntry,UUID> {
    Optional<FinancialEntry> findByIdAndCompanyId(UUID id,UUID companyId);
    boolean existsByCompanyIdAndValuesBankAccountId(UUID companyId,UUID bankId);
    boolean existsByCompanyIdAndValuesPartnerId(UUID companyId,UUID partnerId);
    boolean existsByCompanyIdAndValuesResultCenterId(UUID companyId,UUID centerId);
    @Query("""
            select case when count(entry)>0 then true else false end
            from FinancialEntry entry join FinancialCategory category
              on category.id=entry.values.categoryId and category.company.id=entry.company.id
            where entry.company.id=:company and (category.id=:category or category.parentId=:category)
            """)
    boolean hasCategoryOrChildEntries(@Param("company") UUID company,@Param("category") UUID category);
}
