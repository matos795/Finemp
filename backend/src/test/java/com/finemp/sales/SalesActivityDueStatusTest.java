package com.finemp.sales;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class SalesActivityDueStatusTest {

    @Test
    void derivesOverdueTodayAndUpcomingFromExplicitDate() {
        var today = LocalDate.of(2026, 11, 13);

        assertThat(SalesActivityDueStatus.of(LocalDate.of(2026, 11, 12), false, today))
                .isEqualTo(SalesActivityDueStatus.OVERDUE);
        assertThat(SalesActivityDueStatus.of(today, false, today))
                .isEqualTo(SalesActivityDueStatus.TODAY);
        assertThat(SalesActivityDueStatus.of(LocalDate.of(2026, 11, 14), false, today))
                .isEqualTo(SalesActivityDueStatus.UPCOMING);
        assertThat(SalesActivityDueStatus.of(LocalDate.of(2026, 11, 12), true, today))
                .isEqualTo(SalesActivityDueStatus.COMPLETED);
    }
}
