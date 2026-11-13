package com.finemp.contracts;

import java.time.LocalDate;
import java.time.YearMonth;

final class ContractBillingDate {

    private ContractBillingDate() {
    }

    static LocalDate in(YearMonth month, int billingDay) {
        if (month == null || billingDay < 1 || billingDay > 31) {
            throw new IllegalArgumentException("Competência de faturamento inválida.");
        }
        return month.atDay(Math.min(billingDay, month.lengthOfMonth()));
    }
}
