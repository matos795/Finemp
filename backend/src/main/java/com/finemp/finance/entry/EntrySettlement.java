package com.finemp.finance.entry;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

final class EntrySettlement {
    private EntrySettlement() { }
    static FinancialEntryValues settle(FinancialEntryValues current,EntrySettlementData data) {
        if(current==null || data==null)throw invalid();
        // With canonical cents, a nonzero difference is at least 0.01: equivalent to the reference's >0.004 threshold.
        var actual=data.paidAmount().compareTo(current.nominalAmount())==0?null:data.paidAmount();
        return payment(current,data.paymentDate(),actual,data.bankAccountId());
    }
    static FinancialEntryValues reverse(FinancialEntryValues current) {
        if(current==null)throw invalid();
        return payment(current,null,null,current.bankAccountId());
    }
    private static FinancialEntryValues payment(FinancialEntryValues current,LocalDate date,BigDecimal actual,UUID bank) {
        return new FinancialEntryValues(current.direction(),current.description(),current.nominalAmount(),current.documentDate(),current.entryDate(),current.competence(),current.dueDate(),
                date,actual,bank,current.categoryId(),current.partnerId(),current.resultCenterId(),current.costBehaviorOverride(),current.paymentMethod(),current.documentNumber(),current.notes());
    }
    private static IllegalArgumentException invalid() {return new IllegalArgumentException("Transição financeira inválida.");}
}
