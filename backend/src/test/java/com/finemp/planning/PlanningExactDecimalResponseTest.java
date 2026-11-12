package com.finemp.planning;

import static org.assertj.core.api.Assertions.assertThat;

import com.finemp.company.Company;
import com.finemp.planning.action.ActionPlanItem;
import com.finemp.planning.action.ActionPlanResponse;
import com.finemp.planning.goal.Goal;
import com.finemp.planning.goal.GoalDirection;
import com.finemp.planning.goal.GoalMeasurement;
import com.finemp.planning.goal.GoalMeasurementResponse;
import com.finemp.planning.goal.GoalResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PlanningExactDecimalResponseTest {

    @Test
    void responsesExposeExactDecimalsAsText() {
        var company = new Company(
                "Synthetic Exact Company",
                "00000000000012");

        var goal = new Goal(
                company,
                "Meta sintética",
                GoalDirection.UP,
                new BigDecimal("10.1250"),
                new BigDecimal("20.5000"),
                LocalDate.of(2027, 1, 1),
                null);

        var measurement = new GoalMeasurement(
                goal,
                LocalDate.of(2026, 11, 12),
                new BigDecimal("15.3125"),
                null);

        var goalResponse = GoalResponse.from(goal, measurement);
        var measurementResponse =
                GoalMeasurementResponse.from(measurement);

        assertThat(goalResponse.baselineValue())
                .isEqualTo("10.1250");
        assertThat(goalResponse.targetValue())
                .isEqualTo("20.5000");
        assertThat(goalResponse.currentValue())
                .isEqualTo("15.3125");
        assertThat(goalResponse.progress())
                .matches("\\d+\\.\\d{4}");
        assertThat(measurementResponse.measuredValue())
                .isEqualTo("15.3125");

        var action = new ActionPlanItem(
                company,
                goal,
                "Executar ação",
                null,
                "Responsável sintético",
                null,
                null,
                null,
                new BigDecimal("1234.50"),
                "PLANEJADA");

        assertThat(ActionPlanResponse.from(action).cost())
                .isEqualTo("1234.50");
    }
}
