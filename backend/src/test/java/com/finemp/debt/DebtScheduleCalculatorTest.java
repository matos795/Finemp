package com.finemp.debt;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DebtScheduleCalculatorTest {

    @Test
    void sacMatchesPinnedReferenceAndClosesResidual() {
        var schedule = DebtScheduleCalculator.sac(
                new BigDecimal("1000.00"),
                3,
                new BigDecimal("3.00"),
                LocalDate.of(2026, 1, 31));

        assertThat(schedule).containsExactly(
                new DebtScheduleCalculator.Installment(
                        1,
                        LocalDate.of(2026, 1, 31),
                        new BigDecimal("333.33"),
                        new BigDecimal("30.00"),
                        new BigDecimal("363.33"),
                        new BigDecimal("666.67")),
                new DebtScheduleCalculator.Installment(
                        2,
                        LocalDate.of(2026, 2, 28),
                        new BigDecimal("333.33"),
                        new BigDecimal("20.00"),
                        new BigDecimal("353.33"),
                        new BigDecimal("333.34")),
                new DebtScheduleCalculator.Installment(
                        3,
                        LocalDate.of(2026, 3, 31),
                        new BigDecimal("333.34"),
                        new BigDecimal("10.00"),
                        new BigDecimal("343.34"),
                        new BigDecimal("0.00")));
    }

    @Test
    void zeroRateHasNoInterestAndStillClosesPrincipalExactly() {
        var schedule = DebtScheduleCalculator.sac(
                new BigDecimal("100.00"),
                6,
                BigDecimal.ZERO,
                LocalDate.of(2026, 8, 15));

        assertThat(schedule)
                .extracting(DebtScheduleCalculator.Installment::interest)
                .containsOnly(new BigDecimal("0.00"));
        assertThat(schedule.getLast().balance()).isEqualByComparingTo("0.00");
        assertThat(schedule.stream()
                .map(DebtScheduleCalculator.Installment::amortization)
                .reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("100.00");
    }

    @Test
    void endOfMonthAnchorReturnsToDay31AfterFebruary() {
        var schedule = DebtScheduleCalculator.sac(
                new BigDecimal("300.00"),
                3,
                BigDecimal.ZERO,
                LocalDate.of(2028, 1, 31));

        assertThat(schedule)
                .extracting(DebtScheduleCalculator.Installment::dueDate)
                .containsExactly(
                        LocalDate.of(2028, 1, 31),
                        LocalDate.of(2028, 2, 29),
                        LocalDate.of(2028, 3, 31));
    }

    @Test
    void annualEffectiveRateConvertsToEquivalentMonthlyPercent() {
        var monthly = DebtScheduleCalculator.monthlyPercentFromAnnualPercent(
                new BigDecimal("12.6825030131969720661201"));

        assertThat(monthly).isCloseTo(
                new BigDecimal("1.000000000000"),
                org.assertj.core.data.Offset.offset(new BigDecimal("0.000000000001")));
    }

    @Test
    void impliedRateReturnsZeroWhenPaymentsCannotExceedPrincipal() {
        var rate = DebtScheduleCalculator.impliedMonthlyPercent(
                new BigDecimal("1000.00"),
                new BigDecimal("100.00"),
                10);

        assertThat(rate).isEqualByComparingTo("0");
    }

    @Test
    void impliedRateRecoversOnePercentMonthlyForAnAnnuity() {
        var payment = new BigDecimal("88.84878867834170733999");

        var rate = DebtScheduleCalculator.impliedMonthlyPercent(
                new BigDecimal("1000.00"),
                payment,
                12);

        assertThat(rate).isCloseTo(
                new BigDecimal("1.000000000000"),
                org.assertj.core.data.Offset.offset(new BigDecimal("0.000000001")));
    }
}
