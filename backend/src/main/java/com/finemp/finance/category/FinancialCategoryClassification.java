package com.finemp.finance.category;

/** The same homologated fallback is used by entities and scalar read snapshots. */
final class FinancialCategoryClassification {
    private FinancialCategoryClassification() { }
    static FinancialCategory.EffectiveClassification resolve(FinancialDirection direction,CashActivity activity,OperationalDreLine dre,CostBehavior cost,
            CashActivity parentActivity,OperationalDreLine parentDre,CostBehavior parentCost) {
        if(activity==null)activity=parentActivity;
        if(dre==null)dre=parentDre;
        if(cost==null)cost=parentCost;
        if(cost==null)cost=direction==FinancialDirection.INCOME?CostBehavior.VARIABLE:CostBehavior.FIXED;
        return new FinancialCategory.EffectiveClassification(activity==null?CashActivity.OPERATING:activity,dre==null?OperationalDreLine.NAO:dre,cost);
    }
}
