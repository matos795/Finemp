package com.finemp.finance.reporting;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.List;

/** Confirmed ratios derived exclusively from reports already read in the presentation snapshot. */
public final class FinancialPresentationIndicators {
    private FinancialPresentationIndicators() {}

    static Report calculate(String month, MonthlyCashTotalsService.Report cashYear,
            OpenTitlesAgingService.Report receivables, OperationalDreMonthlyReadService.Report dre) {
        var period = YearMonth.parse(month);
        if (period.getYear() != cashYear.year() || dre != null && !month.equals(dre.month()))
            throw new IllegalArgumentException("Indicadores exigem o mesmo período dos relatórios.");
        var cash = cashYear.months().stream().filter(value -> value.month() == period.getMonthValue())
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Mês de caixa ausente."));
        var generation = cash.realIncome().subtract(cash.realExpense());
        Dre dreIndicators = null;
        if (dre != null) {
            var totals = dre.subtotals();
            boolean available = totals.netRevenue().signum() != 0;
            dreIndicators = new Dre(dre.partial(), dre.coverage(),
                    ratio(totals.grossProfit(), totals.netRevenue(), available),
                    ratio(totals.ebitda(), totals.netRevenue(), available),
                    ratio(totals.netResult(), totals.netRevenue(), available));
        }
        return new Report("PERCENT", 1,
                new Cash(cash.realIncome(), cash.realExpense(), generation,
                        ratio(generation, cash.realIncome(), cash.realIncome().signum() > 0)),
                new Receivables(ratio(receivables.overdueAmount(), receivables.totalAmount(), receivables.totalAmount().signum() > 0)),
                dreIndicators);
    }

    private static Metric ratio(BigDecimal numerator, BigDecimal denominator, boolean available) {
        return new Metric(numerator, denominator,
                available ? numerator.multiply(BigDecimal.valueOf(100)).divide(denominator, 1, RoundingMode.HALF_UP) : null,
                available ? Availability.AVAILABLE : Availability.UNAVAILABLE_BASE);
    }

    public enum Availability { AVAILABLE, UNAVAILABLE_BASE }
    public record Metric(@JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal numerator,
            @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal denominator,
            @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal percent, Availability availability) {}
    public record Cash(@JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal realizedIncome,
            @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal realizedExpense,
            @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal generation, Metric margin) {}
    public record Receivables(Metric overdueRate) {}
    public record Dre(boolean partial, List<String> coverage, Metric grossMargin, Metric ebitdaMargin, Metric netMargin) {
        public Dre { coverage = List.copyOf(coverage); }
    }
    public record Report(String percentUnit, int percentDecimalPlaces, Cash cash, Receivables receivables,
            @JsonInclude(JsonInclude.Include.NON_NULL) Dre dre) {}
}
