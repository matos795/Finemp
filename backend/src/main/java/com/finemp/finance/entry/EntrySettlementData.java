package com.finemp.finance.entry;

import java.math.*;
import java.time.LocalDate;
import java.util.UUID;

/** Already canonical positive cents, not a raw-capture or legacy rounding policy. */
public record EntrySettlementData(LocalDate paymentDate,BigDecimal paidAmount,UUID bankAccountId) {
    public EntrySettlementData {
        if(paymentDate==null || paymentDate.getYear()<1 || paymentDate.getYear()>9999 || bankAccountId==null || paidAmount==null || paidAmount.signum()<=0)throw invalid();
        try {
            var compact=paidAmount.stripTrailingZeros();
            if(compact.scale()>2 || (long)compact.precision()-compact.scale()>17)throw invalid();
            paidAmount=compact.setScale(2,RoundingMode.UNNECESSARY);
        }catch(ArithmeticException invalid) {throw invalid();}
    }
    private static IllegalArgumentException invalid() {return new IllegalArgumentException("Dados canônicos de baixa inválidos.");}
    @Override public String toString() {return "EntrySettlementData[redacted]";}
}
