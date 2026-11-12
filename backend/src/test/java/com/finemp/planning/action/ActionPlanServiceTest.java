package com.finemp.planning.action;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.finemp.administration.SaasResourceNotFoundException;
import com.finemp.company.Company;
import com.finemp.company.CompanyRepository;
import com.finemp.planning.PlanningAuthorization;
import com.finemp.planning.PlanningConflictException;
import com.finemp.planning.goal.GoalRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ActionPlanServiceTest {

    @Mock ActionPlanItemRepository actions;
    @Mock GoalRepository goals;
    @Mock CompanyRepository companies;
    @Mock PlanningAuthorization authorization;

    private final Clock clock = Clock.systemUTC();

    @Test
    void createNeverAcceptsAGoalFromAnotherCompanyLookup() {
        var actor = UUID.randomUUID();
        var companyId = UUID.randomUUID();
        var goalId = UUID.randomUUID();
        var company = new Company("Synthetic Company", "00000000000006");

        when(companies.findLockedById(companyId))
                .thenReturn(Optional.of(company));
        when(goals.findByIdAndCompanyId(goalId, companyId))
                .thenReturn(Optional.empty());

        var service = new ActionPlanService(
                actions,
                goals,
                companies,
                authorization,
                clock);

        var request = new ActionPlanCreateRequest(
                goalId,
                "Implantar rotina",
                "Melhorar execução",
                "Responsável sintético",
                LocalDate.of(2026, 12, 1),
                "FINEMP",
                "Executar e revisar",
                new BigDecimal("100.00"),
                "PLANEJADA");

        assertThrows(
                SaasResourceNotFoundException.class,
                () -> service.create(actor, companyId, request));

        verify(actions, never()).saveAndFlush(
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void staleVersionBlocksActionMutation() {
        var actor = UUID.randomUUID();
        var companyId = UUID.randomUUID();
        var actionId = UUID.randomUUID();
        var company = new Company("Synthetic Company", "00000000000007");
        var item = new ActionPlanItem(
                company,
                null,
                "Ação",
                null,
                "Responsável",
                null,
                null,
                null,
                null,
                "PLANEJADA");

        when(actions.findLockedByIdAndCompanyId(actionId, companyId))
                .thenReturn(Optional.of(item));

        var service = new ActionPlanService(
                actions,
                goals,
                companies,
                authorization,
                clock);

        var request = new ActionPlanUpdateRequest(
                null,
                "Ação atualizada",
                null,
                "Responsável",
                null,
                null,
                null,
                null,
                "PLANEJADA",
                1L);

        assertThrows(
                PlanningConflictException.class,
                () -> service.update(actor, companyId, actionId, request));

        verify(actions, never()).flush();
    }
}
