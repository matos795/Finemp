package com.finemp.accounting.v2read;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Totais de débito e crédito do período, como publicados pela origem (#474).
 * Não é saldo nem saldo acumulado; valores como string decimal na escala armazenada.
 */
public record AccountingDebitCreditTotalsPageResponse(
        UUID accountingSourceId,
        DebitCreditTotalsGranularity granularity,
        LocalDate startDate,
        LocalDate endDate,
        List<Item> items,
        int page,
        int size,
        long totalItems,
        int totalPages) {

    static final int AMOUNT_SCALE = 4;

    public record Item(
            String period,
            String establishmentCode,
            String sourceAccountId,
            UUID accountId,
            String entryTypeCode,
            String debitTotal,
            String creditTotal) {

        static Item from(DebitCreditTotalsGranularity granularity, AccountingDebitCreditTotalsRow row) {
            String period = granularity == DebitCreditTotalsGranularity.MONTHLY
                    ? YearMonth.from(row.periodStart()).toString()
                    : row.periodStart().toString();
            return new Item(period, row.establishmentCode(), row.sourceAccountId(), row.accountId(),
                    row.entryTypeCode(), exact(row.debitTotal()), exact(row.creditTotal()));
        }
    }

    public AccountingDebitCreditTotalsPageResponse {
        items = List.copyOf(items);
    }

    static String exact(BigDecimal value) {
        return value.setScale(AMOUNT_SCALE, RoundingMode.UNNECESSARY).toPlainString();
    }
}
