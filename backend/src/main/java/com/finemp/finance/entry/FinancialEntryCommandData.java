package com.finemp.finance.entry;

import com.finemp.finance.category.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

/** Internal canonical-cent contract. Raw capture/rounding and monetary series remain separate. */
public record FinancialEntryCommandData(FinancialDirection direction,String description,BigDecimal nominalAmount,
        LocalDate documentDate,LocalDate entryDate,YearMonth competence,LocalDate dueDate,LocalDate paymentDate,
        UUID bankAccountId,UUID categoryId,UUID partnerId,UUID resultCenterId,CostBehavior costBehaviorOverride,
        String paymentMethod,String documentNumber,String notes) {
    public FinancialEntryCommandData {
        var canonical=new FinancialEntryValues(direction,description,nominalAmount,documentDate,entryDate,competence,dueDate,paymentDate,null,
                bankAccountId,categoryId,partnerId,resultCenterId,costBehaviorOverride,paymentMethod,documentNumber,notes);
        if(canonical.nominalAmount().signum()<=0)throw new IllegalArgumentException("Valor canônico do lançamento deve ser positivo.");
        description=canonical.description();nominalAmount=canonical.nominalAmount();
        paymentMethod=canonical.paymentMethod();documentNumber=canonical.documentNumber();notes=canonical.notes();
    }
    FinancialEntryValues values(BigDecimal previousSettlement) {
        return new FinancialEntryValues(direction,description,nominalAmount,documentDate,entryDate,competence,dueDate,paymentDate,
                paymentDate==null?null:previousSettlement,bankAccountId,categoryId,partnerId,resultCenterId,costBehaviorOverride,paymentMethod,documentNumber,notes);
    }
    @Override public String toString() {return "FinancialEntryCommandData[redacted]";}
}
