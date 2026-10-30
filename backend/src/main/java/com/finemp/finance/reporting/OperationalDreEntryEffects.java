package com.finemp.finance.reporting;

import com.finemp.finance.category.*;
import com.finemp.finance.entry.FinancialEntryValues;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;

/** Pure F06 entry effects. Not a full operational DRE or the accounting Questor DRE. */
final class OperationalDreEntryEffects {
    private OperationalDreEntryEffects(){}
    static List<Effect> calculate(FinancialEntryValues entry,OperationalDreLine effectiveLine){
        if(entry==null || effectiveLine==null)throw new IllegalArgumentException("Lançamento e linha efetiva são obrigatórios.");
        // Preserve the observed exclusion; interest on NAO remains a separate business decision.
        if(effectiveLine==OperationalDreLine.NAO)return List.of();
        boolean income=entry.direction()==FinancialDirection.INCOME;
        var nominal=income?entry.nominalAmount():entry.nominalAmount().negate();
        var effects=new ArrayList<Effect>(2);
        effects.add(new Effect(entry.competence(),effectiveLine,entry.categoryId(),Source.COMPETENCE,nominal));
        if(entry.paymentDate()!=null){
            var difference=entry.effectiveAmount().subtract(entry.nominalAmount());
            if(!income)difference=difference.negate();
            // Canonical cents: every nonzero difference exceeds the reference's 0.004 threshold.
            if(difference.signum()!=0)effects.add(new Effect(YearMonth.from(entry.paymentDate()),difference.signum()>0?OperationalDreLine.RFIN:OperationalDreLine.DFIN,entry.categoryId(),Source.SETTLEMENT_DIFFERENCE,difference));
        }
        return List.copyOf(effects);
    }
    enum Source { COMPETENCE,SETTLEMENT_DIFFERENCE }
    record Effect(YearMonth period,OperationalDreLine line,UUID categoryId,Source source,BigDecimal amount){}
}
