package com.finemp.billing;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ServiceInvoiceTaxCalculatorTest {

    @Test
    void retainedIssAndFederalWithholdingsReduceNetAmount() {
        var result = ServiceInvoiceTaxCalculator.calculate(
                new BigDecimal("1000.00"),
                new BigDecimal("5.00"),
                true,
                new BigDecimal("1.50"),
                new BigDecimal("0.65"),
                new BigDecimal("3.00"),
                new BigDecimal("1.00"),
                BigDecimal.ZERO);

        assertThat(result.iss()).isEqualByComparingTo("50.00");
        assertThat(result.ir()).isEqualByComparingTo("15.00");
        assertThat(result.pis()).isEqualByComparingTo("6.50");
        assertThat(result.cofins()).isEqualByComparingTo("30.00");
        assertThat(result.csll()).isEqualByComparingTo("10.00");
        assertThat(result.inss()).isEqualByComparingTo("0.00");
        assertThat(result.totalWithheld()).isEqualByComparingTo("111.50");
        assertThat(result.netAmount()).isEqualByComparingTo("888.50");
    }

    @Test
    void nonRetainedIssDoesNotReduceNetAmount() {
        var result = ServiceInvoiceTaxCalculator.calculate(
                new BigDecimal("1000.00"),
                new BigDecimal("5.00"),
                false,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO);

        assertThat(result.iss()).isEqualByComparingTo("0.00");
        assertThat(result.totalWithheld()).isEqualByComparingTo("0.00");
        assertThat(result.netAmount()).isEqualByComparingTo("1000.00");
    }

    @Test
    void eachTaxRoundsToMoneyBeforeTotaling() {
        var result = ServiceInvoiceTaxCalculator.calculate(
                new BigDecimal("333.33"),
                BigDecimal.ZERO,
                false,
                new BigDecimal("1.50"),
                new BigDecimal("0.65"),
                new BigDecimal("3.00"),
                new BigDecimal("1.00"),
                new BigDecimal("11.00"));

        assertThat(result.ir()).isEqualByComparingTo("5.00");
        assertThat(result.pis()).isEqualByComparingTo("2.17");
        assertThat(result.cofins()).isEqualByComparingTo("10.00");
        assertThat(result.csll()).isEqualByComparingTo("3.33");
        assertThat(result.inss()).isEqualByComparingTo("36.67");
        assertThat(result.totalWithheld()).isEqualByComparingTo("57.17");
        assertThat(result.netAmount()).isEqualByComparingTo("276.16");
    }
}
