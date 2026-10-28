package com.finemp.finance.category;

import java.util.UUID;

/** Scalars from one SQL snapshot: never reuse a previously managed category or parent. */
public record FinancialCategorySnapshot(UUID id,UUID companyId,UUID parentId,FinancialCategory.Kind kind,FinancialDirection direction,
        String code,String name,CashActivity cashActivity,OperationalDreLine dreLine,CostBehavior costBehavior,long version,
        CashActivity parentActivity,OperationalDreLine parentDre,CostBehavior parentCost) {
    FinancialCategoryResponse response() {
        return new FinancialCategoryResponse(id,companyId,parentId,kind,direction,code,name,cashActivity,dreLine,costBehavior,
                FinancialCategoryClassification.resolve(direction,cashActivity,dreLine,costBehavior,parentActivity,parentDre,parentCost),version);
    }
}
