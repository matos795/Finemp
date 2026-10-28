package com.finemp.finance.entry;

import com.finemp.finance.category.CostBehavior;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Value projection only: no managed entity is returned by this query. */
public record FinancialEntryReadRow(UUID id,UUID companyId,FinancialEntryValues values,String bankName,String categoryCode,String categoryName,
        String partnerName,String centerCode,String centerName,CostBehavior effectiveCostBehavior,Instant createdAt,Instant updatedAt,long version) {
    FinancialEntryResponse response(LocalDate asOf) {
        var status=values.paymentDate()!=null?EntryReadStatus.PAID:values.dueDate().isBefore(asOf)?EntryReadStatus.OVERDUE:EntryReadStatus.OPEN;
        return new FinancialEntryResponse(id,companyId,values,new FinancialEntryResponse.Labels(bankName,categoryCode,categoryName,partnerName,centerCode,centerName),
                effectiveCostBehavior,values.effectiveAmount(),status,asOf,createdAt,updatedAt,version);
    }
    @Override public String toString() {return "FinancialEntryReadRow[id="+id+"]";}
}
