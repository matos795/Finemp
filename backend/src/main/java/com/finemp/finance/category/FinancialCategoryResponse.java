package com.finemp.finance.category;

import java.util.UUID;

public record FinancialCategoryResponse(UUID id,UUID companyId,UUID parentId,FinancialCategory.Kind kind,FinancialDirection direction,
        String code,String name,CashActivity cashActivity,OperationalDreLine dreLine,CostBehavior costBehavior,
        FinancialCategory.EffectiveClassification effective,long version) {
    static FinancialCategoryResponse from(FinancialCategory row,FinancialCategory parent) {
        return new FinancialCategoryResponse(row.getId(),row.getCompany().getId(),row.getParentId(),row.getKind(),row.getDirection(),row.getCode(),
                row.getName(),row.getCashActivity(),row.getDreLine(),row.getCostBehavior(),row.effective(parent),row.getVersion());
    }
}
