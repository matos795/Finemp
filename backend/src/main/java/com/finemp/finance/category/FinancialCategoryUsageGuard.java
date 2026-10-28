package com.finemp.finance.category;

import java.util.UUID;

public interface FinancialCategoryUsageGuard {
    /** For a group this includes entries on every direct subcategory. */
    boolean hasEntriesInCategoryOrChildren(UUID companyId,UUID categoryId);
}
