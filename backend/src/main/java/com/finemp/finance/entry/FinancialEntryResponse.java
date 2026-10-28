package com.finemp.finance.entry;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.finemp.finance.category.CostBehavior;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

public record FinancialEntryResponse(UUID id,UUID companyId,FinancialEntryValues values,Labels labels,CostBehavior effectiveCostBehavior,
        @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal effectiveAmount,EntryReadStatus status,LocalDate asOfDate,
        Instant createdAt,Instant updatedAt,long version,@JsonInclude(JsonInclude.Include.NON_NULL) SeriesInfo series) {
    public record SeriesInfo(UUID id,SeriesMode mode,int number,int quantity) { }
    public record Labels(String bankName,String categoryCode,String categoryName,String partnerName,String centerCode,String centerName) {
        @Override public String toString() {return "FinancialEntryLabels[redacted]";}
    }
    @Override public String toString() {return "FinancialEntryResponse[id="+id+"]";}
}
