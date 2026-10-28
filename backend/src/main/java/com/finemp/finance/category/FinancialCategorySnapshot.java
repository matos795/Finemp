package com.finemp.finance.category;

/** Both entities are selected by one SQL statement and share its MVCC snapshot. */
public record FinancialCategorySnapshot(FinancialCategory category,FinancialCategory parent) {
    FinancialCategoryResponse response() {return FinancialCategoryResponse.from(category,parent);}
}
