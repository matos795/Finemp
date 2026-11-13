package com.finemp.finance.reporting;

import com.finemp.finance.category.FinancialDirection;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class WorkingCapitalCycleCalculatorTest {

    private static final LocalDate BASE = LocalDate.of(2026, 11, 1);

    @Test
    void weightedTermsUseActualPaymentWhenAvailableAndGrantedTermOtherwise() {
        var result = WorkingCapitalCycleCalculator.calculate(
                List.of(
                        entry(FinancialDirection.INCOME, "100.00", 10, 30, 10),
                        entry(FinancialDirection.INCOME, "300.00", 20, 40, null),
                        entry(FinancialDirection.EXPENSE, "200.00", 15, 45, 12)),
                new BigDecimal("5.00"),
                30);

        assertThat(result.receivablesDays()).isEqualByComparingTo("17.50");
        assertThat(result.payablesDays()).isEqualByComparingTo("12.00");
        assertThat(result.inventoryDays()).isEqualByComparingTo("5.00");
        assertThat(result.operatingCycleDays()).isEqualByComparingTo("22.50");
        assertThat(result.financialCycleDays()).isEqualByComparingTo("10.50");
        assertThat(result.dailyRevenue()).isEqualByComparingTo("13.3333333333");
        assertThat(result.cycleWorkingCapital()).isEqualByComparingTo("140.00");
    }

    @Test
    void openTitlesUseGrantedDueTermAndZeroWeightDoesNotCreateFalseAverage() {
        var result = WorkingCapitalCycleCalculator.calculate(
                List.of(
                        entry(FinancialDirection.INCOME, "0.00", 10, 30, null),
                        entry(FinancialDirection.EXPENSE, "0.00", 20, 20, null)),
                null,
                30);

        assertThat(result.receivablesDays()).isNull();
        assertThat(result.payablesDays()).isNull();
        assertThat(result.inventoryDays()).isNull();
        assertThat(result.operatingCycleDays()).isNull();
        assertThat(result.financialCycleDays()).isNull();
        assertThat(result.cycleWorkingCapital()).isNull();
        assertThat(result.dailyRevenue()).isEqualByComparingTo("0.0000000000");
    }

    @Test
    void earlySettlementKeepsNegativeRealTerm() {
        var result = WorkingCapitalCycleCalculator.calculate(
                List.of(entry(FinancialDirection.INCOME, "100.00", 30, 30, -2)),
                BigDecimal.ZERO,
                30);

        assertThat(result.receivablesDays()).isEqualByComparingTo("-2.00");
        assertThat(result.operatingCycleDays()).isEqualByComparingTo("-2.00");
    }

    private static WorkingCapitalCycleCalculator.Entry entry(
            FinancialDirection direction,
            String amount,
            int grantedDays,
            int ignoredDueDays,
            Integer paidDays) {
        LocalDate dueDate = BASE.plusDays(grantedDays);
        LocalDate paymentDate = paidDays == null ? null : BASE.plusDays(paidDays);
        return new WorkingCapitalCycleCalculator.Entry(
                direction,
                new BigDecimal(amount),
                BASE,
                dueDate,
                paymentDate);
    }
}
