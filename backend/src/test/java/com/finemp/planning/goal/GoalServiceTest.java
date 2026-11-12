package com.finemp.planning.goal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.finemp.company.Company;
import com.finemp.company.CompanyRepository;
import com.finemp.planning.PlanningAuthorization;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoalServiceTest {

    @Mock GoalRepository goals;
    @Mock GoalMeasurementRepository measurements;
    @Mock CompanyRepository companies;
    @Mock PlanningAuthorization authorization;

    @Test
    void measurementRevalidatesManageAfterLockAndAppendsHistory() {
        var actor = UUID.randomUUID();
        var companyId = UUID.randomUUID();
        var goalId = UUID.randomUUID();
        var company = new Company("Synthetic Goal Company", "00000000000008");
        var goal = new Goal(
                company,
                "Reduzir prazo",
                GoalDirection.DOWN,
                new BigDecimal("10"),
                new BigDecimal("5"),
                LocalDate.of(2027, 1, 1),
                null);

        when(goals.findLockedByIdAndCompanyId(goalId, companyId))
                .thenReturn(Optional.of(goal));
        when(measurements.saveAndFlush(
                org.mockito.ArgumentMatchers.any(GoalMeasurement.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var service = new GoalService(
                goals,
                measurements,
                companies,
                authorization);

        service.measure(
                actor,
                companyId,
                goalId,
                new GoalMeasurementRequest(
                        LocalDate.of(2026, 11, 12),
                        new BigDecimal("8"),
                        "Primeira medição"));

        verify(authorization, times(2))
                .requireManage(actor, companyId);

        var captor = ArgumentCaptor.forClass(GoalMeasurement.class);
        verify(measurements).saveAndFlush(captor.capture());

        assertThat(captor.getValue().getMeasuredValue())
                .isEqualByComparingTo("8");
        assertThat(captor.getValue().getGoal()).isSameAs(goal);
    }
}
