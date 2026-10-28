package com.finemp.finance.period;

import java.util.UUID;

public record FinancialPeriodReceipt(UUID companyId,UUID commandId,UUID periodId,String period,boolean closed,long version,UUID auditEventId) {
    public FinancialPeriodReceipt {
        if(companyId==null || commandId==null || periodId==null || auditEventId==null || version<0 || period==null
                || !period.matches("[0-9]{4}-(0[1-9]|1[0-2])") || period.startsWith("0000"))throw new IllegalArgumentException("Recibo de período inválido.");
    }
}
