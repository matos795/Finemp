package com.finemp.finance.reporting;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static com.finemp.finance.reporting.OpenTitlesAgingService.Band.*;

class OpenTitlesAgingBandTest {
    @Test void classifiesBoundaryDaysWithoutTreatingTodayAsOverdue(){
        assertThat(forDays(0)).isEqualTo(DUE_0_30);assertThat(forDays(-30)).isEqualTo(DUE_0_30);assertThat(forDays(-31)).isEqualTo(DUE_31_60);
        assertThat(forDays(-60)).isEqualTo(DUE_31_60);assertThat(forDays(-61)).isEqualTo(DUE_61_90);assertThat(forDays(-90)).isEqualTo(DUE_61_90);assertThat(forDays(-91)).isEqualTo(DUE_OVER_90);
        assertThat(forDays(1)).isEqualTo(OVERDUE_1_30);assertThat(forDays(30)).isEqualTo(OVERDUE_1_30);assertThat(forDays(31)).isEqualTo(OVERDUE_31_60);
        assertThat(forDays(60)).isEqualTo(OVERDUE_31_60);assertThat(forDays(61)).isEqualTo(OVERDUE_61_90);assertThat(forDays(90)).isEqualTo(OVERDUE_61_90);assertThat(forDays(91)).isEqualTo(OVERDUE_OVER_90);
    }
}
