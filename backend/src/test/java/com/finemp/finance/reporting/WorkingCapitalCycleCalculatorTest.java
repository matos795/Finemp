package com.finemp.finance.reporting;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkingCapitalCycleCalculatorTest {

    private static final LocalDate FROM = LocalDate.of(2026, 11, 1);
    private static final LocalDate TO = LocalDate.of(2026, 11, 30);

    @Test
    void preservesWeightedGrantedRealDelayPunctualityAndCycleFormulas() {
        var rows = List.of(
                row(WorkingCapitalCycleCalculator.Side.RECEIVABLE,
                        "customer-a", "Cliente A",
                        "100.00", "110.00", 1, 11, 16),
                row(WorkingCapitalCycleCalculator.Side.RECEIVABLE,
                        "customer-b", "Cliente B",
                        "300.00", null, 1, 21, null),
                row(WorkingCapitalCycleCalculator.Side.PAYABLE,
                        "supplier-a", "Fornecedor A",
                        "200.00", "190.00", 1, 11, 10),
                row(WorkingCapitalCycleCalculator.Side.PAYABLE,
                        "supplier-b", "Fornecedor B",
                        "200.00", "210.00", 1, 21, 25));

        var report = WorkingCapitalCycleCalculator.calculate(
                FROM, TO, TO, 5, rows);

        assertThat(report.daysInPeriod()).isEqualTo(30);

        assertThat(report.receivables().volume()).isEqualByComparingTo("400.00");
        assertThat(report.receivables().grantedDays()).isEqualByComparingTo("17.50");
        assertThat(report.receivables().realDays()).isEqualByComparingTo("15.00");
        assertThat(report.receivables().averageDelayDays()).isEqualByComparingTo("5.00");
        assertThat(report.receivables().punctualityPercent()).isEqualByComparingTo("0.00");
        assertThat(report.receivables().interest()).isEqualByComparingTo("10.00");
        assertThat(report.receivables().openAmount()).isEqualByComparingTo("300.00");
        assertThat(report.receivables().overdueAmount()).isEqualByComparingTo("300.00");

        assertThat(report.payables().grantedDays()).isEqualByComparingTo("15.00");
        assertThat(report.payables().realDays()).isEqualByComparingTo("16.88");
        assertThat(report.payables().punctualityPercent()).isEqualByComparingTo("47.50");
        assertThat(report.payables().interest()).isEqualByComparingTo("10.00");
        assertThat(report.payables().discounts()).isEqualByComparingTo("10.00");

        assertThat(report.pmrDays()).isEqualByComparingTo("15.00");
        assertThat(report.pmpDays()).isEqualByComparingTo("16.88");
        assertThat(report.operatingCycleDays()).isEqualByComparingTo("20.00");
        assertThat(report.financialCycleDays()).isEqualByComparingTo("3.13");
        assertThat(report.workingCapitalForCycle()).isEqualByComparingTo("41.67");
    }

    @Test
    void fallsBackToGrantedTermsAndLeavesMetricsNullWithoutAWeightedBase() {
        var receivable = row(
                WorkingCapitalCycleCalculator.Side.RECEIVABLE,
                null, "Sem cadastro",
                "120.00", null, 1, 31, null);

        var onlyReceivables = WorkingCapitalCycleCalculator.calculate(
                FROM, TO, TO, 0, List.of(receivable));

        assertThat(onlyReceivables.pmrDays()).isEqualByComparingTo("30.00");
        assertThat(onlyReceivables.receivables().realDays()).isNull();
        assertThat(onlyReceivables.receivables().punctualityPercent()).isNull();
        assertThat(onlyReceivables.pmpDays()).isNull();
        assertThat(onlyReceivables.financialCycleDays()).isNull();
        assertThat(onlyReceivables.workingCapitalForCycle()).isNull();

        var empty = WorkingCapitalCycleCalculator.calculate(
                FROM, TO, TO, 0, List.of());

        assertThat(empty.receivables().grantedDays()).isNull();
        assertThat(empty.receivables().dailyVolume()).isEqualByComparingTo("0.00");
        assertThat(empty.pmrDays()).isNull();
        assertThat(empty.pmpDays()).isNull();
    }

    @Test
    void keepsAllOpenTitlesInBalanceMetricsButPeriodLimitsIssuedAndPaidMetrics() {
        var oldOpen = new WorkingCapitalCycleCalculator.Row(
                WorkingCapitalCycleCalculator.Side.RECEIVABLE,
                null,
                "Sem cadastro",
                new BigDecimal("90.00"),
                new BigDecimal("90.00"),
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 10, 1),
                null);
        var current = row(
                WorkingCapitalCycleCalculator.Side.RECEIVABLE,
                "customer-a", "Cliente A",
                "300.00", null, 1, 16, null);

        var report = WorkingCapitalCycleCalculator.calculate(
                FROM, TO, TO, 0, List.of(oldOpen, current));

        assertThat(report.receivables().volume()).isEqualByComparingTo("300.00");
        assertThat(report.receivables().openAmount()).isEqualByComparingTo("390.00");
        assertThat(report.receivables().overdueAmount()).isEqualByComparingTo("390.00");
        assertThat(report.receivables().balanceDays()).isEqualByComparingTo("39.00");
    }

    private static WorkingCapitalCycleCalculator.Row row(
            WorkingCapitalCycleCalculator.Side side,
            String partnerId,
            String partnerName,
            String nominal,
            String effective,
            int documentDay,
            int dueDay,
            Integer paymentDay) {
        return new WorkingCapitalCycleCalculator.Row(
                side,
                partnerId,
                partnerName,
                new BigDecimal(nominal),
                effective == null ? new BigDecimal(nominal) : new BigDecimal(effective),
                LocalDate.of(2026, 11, documentDay),
                LocalDate.of(2026, 11, dueDay),
                paymentDay == null ? null : LocalDate.of(2026, 11, paymentDay));
    }
}
