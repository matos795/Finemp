package com.finemp.finance.entry;

import com.finemp.finance.category.*;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;

public record FinancialEntryFilter(
        @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
        @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,
        FinancialDirection direction,EntryReadStatus status,UUID bankId,UUID categoryId,UUID partnerId,UUID centerId,
        Boolean withoutCenter,CostBehavior costBehavior,String search,
        @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate asOfDate) {
    public static FinancialEntryFilter empty() {return new FinancialEntryFilter(null,null,null,null,null,null,null,null,null,null,null,null);}
}
