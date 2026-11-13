package com.finemp.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class ContractBillingDateTest {

    @Test
    void capsBillingDayAtTheLastDayOfFebruary() {
        assertThat(ContractBillingDate.in(YearMonth.of(2026, 2), 31))
                .isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    void preservesBillingDayWhenItExistsInTheMonth() {
        assertThat(ContractBillingDate.in(YearMonth.of(2026, 11), 15))
                .isEqualTo(LocalDate.of(2026, 11, 15));
    }
}
