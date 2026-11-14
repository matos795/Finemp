package com.finemp.assets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class AssetDepreciationCalculatorTest {

    @Test
    void oracleDisposalMonthStillDepreciatesAndFollowingMonthStops() {
        var asset = input(
                "1200.00",
                12,
                "0",
                true,
                "2026-01-10",
                "2026-03-15");

        assertThat(AssetDepreciationCalculator.depreciationForMonth(
                asset,
                YearMonth.of(2026, 3)))
                .isEqualByComparingTo("100.00");

        assertThat(AssetDepreciationCalculator.depreciationForMonth(
                asset,
                YearMonth.of(2026, 4)))
                .isEqualByComparingTo("0.00");
    }

    @Test
    void oracleAccumulationStopsAtDisposalMonth() {
        var asset = input(
                "1200.00",
                12,
                "0",
                true,
                "2026-01-10",
                "2026-03-15");

        assertThat(AssetDepreciationCalculator.accumulatedDepreciation(
                asset,
                LocalDate.of(2026, 12, 31)))
                .isEqualByComparingTo("300.00");
    }

    @Test
    void acquisitionMonthCountsAndUsefulLifeCapsAccumulation() {
        var asset = input(
                "1200.00",
                12,
                "0",
                true,
                "2026-01-31",
                null);

        assertThat(AssetDepreciationCalculator.depreciationForMonth(
                asset,
                YearMonth.of(2026, 1)))
                .isEqualByComparingTo("100.00");

        assertThat(AssetDepreciationCalculator.accumulatedDepreciation(
                asset,
                LocalDate.of(2027, 6, 30)))
                .isEqualByComparingTo("1200.00");

        assertThat(AssetDepreciationCalculator.bookValue(
                asset,
                LocalDate.of(2027, 6, 30)))
                .isEqualByComparingTo("0.00");
    }

    @Test
    void residualPercentageReducesDepreciableBaseAndPreservesResidualBookValue() {
        var asset = input(
                "1200.00",
                12,
                "10",
                true,
                "2026-01-01",
                null);

        assertThat(AssetDepreciationCalculator.monthlyDepreciation(asset))
                .isEqualByComparingTo("90.00");

        assertThat(AssetDepreciationCalculator.accumulatedDepreciation(
                asset,
                LocalDate.of(2026, 12, 31)))
                .isEqualByComparingTo("1080.00");

        assertThat(AssetDepreciationCalculator.bookValue(
                asset,
                LocalDate.of(2026, 12, 31)))
                .isEqualByComparingTo("120.00");
    }

    @Test
    void accumulatedDepreciationNeverExceedsBaseWhenRoundedMonthlyValueWouldOvershoot() {
        var asset = input(
                "100.00",
                6,
                "0",
                true,
                "2026-01-01",
                null);

        assertThat(AssetDepreciationCalculator.monthlyDepreciation(asset))
                .isEqualByComparingTo("16.67");

        assertThat(AssetDepreciationCalculator.accumulatedDepreciation(
                asset,
                LocalDate.of(2026, 5, 31)))
                .isEqualByComparingTo("83.35");

        assertThat(AssetDepreciationCalculator.accumulatedDepreciation(
                asset,
                LocalDate.of(2026, 6, 30)))
                .isEqualByComparingTo("100.00");
    }

    @Test
    void nonDepreciableAssetKeepsOriginalBookValue() {
        var asset = input(
                "1200.00",
                12,
                "0",
                false,
                "2026-01-01",
                null);

        assertThat(AssetDepreciationCalculator.monthlyDepreciation(asset))
                .isEqualByComparingTo("0.00");
        assertThat(AssetDepreciationCalculator.accumulatedDepreciation(
                asset,
                LocalDate.of(2030, 1, 1)))
                .isEqualByComparingTo("0.00");
        assertThat(AssetDepreciationCalculator.bookValue(
                asset,
                LocalDate.of(2030, 1, 1)))
                .isEqualByComparingTo("1200.00");
    }

    @Test
    void datesBeforeAcquisitionDoNotDepreciate() {
        var asset = input(
                "1200.00",
                12,
                "0",
                true,
                "2026-03-15",
                null);

        assertThat(AssetDepreciationCalculator.depreciationForMonth(
                asset,
                YearMonth.of(2026, 2)))
                .isEqualByComparingTo("0.00");
        assertThat(AssetDepreciationCalculator.accumulatedDepreciation(
                asset,
                LocalDate.of(2026, 2, 28)))
                .isEqualByComparingTo("0.00");
    }

    @Test
    void disposalResultUsesBookValueAtDisposalMonthForGainAndLoss() {
        var asset = input(
                "1200.00",
                12,
                "0",
                true,
                "2026-01-10",
                "2026-03-15");

        assertThat(AssetDepreciationCalculator.bookValueAtDisposal(asset))
                .isEqualByComparingTo("900.00");
        assertThat(AssetDepreciationCalculator.disposalGainLoss(
                asset,
                new BigDecimal("1000.00")))
                .isEqualByComparingTo("100.00");
        assertThat(AssetDepreciationCalculator.disposalGainLoss(
                asset,
                new BigDecimal("800.00")))
                .isEqualByComparingTo("-100.00");
    }

    @Test
    void rejectsInvalidLifeResidualDatesAndNegativeAmounts() {
        assertThatThrownBy(() -> input(
                "1200.00",
                0,
                "0",
                true,
                "2026-01-01",
                null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> input(
                "1200.00",
                12,
                "100.01",
                true,
                "2026-01-01",
                null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> input(
                "-1.00",
                12,
                "0",
                true,
                "2026-01-01",
                null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> input(
                "1200.00",
                12,
                "0",
                true,
                "2026-03-01",
                "2026-02-28"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static AssetDepreciationCalculator.Input input(
            String value,
            int lifeMonths,
            String residualPercent,
            boolean depreciable,
            String acquisitionDate,
            String disposalDate) {
        return new AssetDepreciationCalculator.Input(
                new BigDecimal(value),
                lifeMonths,
                new BigDecimal(residualPercent),
                depreciable,
                LocalDate.parse(acquisitionDate),
                disposalDate == null ? null : LocalDate.parse(disposalDate));
    }
}
