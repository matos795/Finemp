package com.finemp.finance.reporting;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.finemp.access.CompanyPermission;
import com.finemp.access.CompanyPermissionAuthorizationService;
import com.finemp.administration.OrganizationAccessDeniedException;
import com.finemp.finance.category.OperationalDreLine;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CashVsOperationalDreServiceTest {

    private final CompanyPermissionAuthorizationService permissions =
            mock(CompanyPermissionAuthorizationService.class);
    private final MonthlyCashTotalsService monthlyCash =
            mock(MonthlyCashTotalsService.class);
    private final OperationalDreMonthlyReadService operationalDre =
            mock(OperationalDreMonthlyReadService.class);
    private final CashVsOperationalDreService service =
            new CashVsOperationalDreService(permissions, monthlyCash, operationalDre);

    private final UUID actor = UUID.randomUUID();
    private final UUID company = UUID.randomUUID();

    @Test
    void unauthorizedUserDoesNotReadEitherFinancialReport() {
        assertThatThrownBy(() -> service.compare(actor, company, "2026-11"))
                .isInstanceOf(OrganizationAccessDeniedException.class);

        verifyNoInteractions(monthlyCash, operationalDre);
    }

    @Test
    void invalidMonthDoesNotReadEitherFinancialReport() {
        when(permissions.hasFreshPermission(
                actor,
                company,
                CompanyPermission.FINANCIAL_ENTRY_VIEW))
                .thenReturn(true);

        assertThatThrownBy(() -> service.compare(actor, company, "2026-13"))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(monthlyCash, operationalDre);
    }

    @Test
    void comparesTheSameMonthAndCalculatesSignedDifferenceInBackend() {
        when(permissions.hasFreshPermission(
                actor,
                company,
                CompanyPermission.FINANCIAL_ENTRY_VIEW))
                .thenReturn(true);

        when(monthlyCash.report(actor, company, 2026))
                .thenReturn(cashReport(11, "1800.00", "700.00"));
        when(operationalDre.report(actor, company, "2026-11"))
                .thenReturn(dreReport("2026-11", "1500.00", "-500.00"));

        var report = service.compare(actor, company, "2026-11");

        assertThat(report.month()).isEqualTo("2026-11");
        assertThat(report.partial()).isTrue();
        assertThat(report.coverage()).containsExactly("FINANCIAL_ENTRIES");
        assertThat(report.cash().realizedIncome()).isEqualByComparingTo("1800.00");
        assertThat(report.cash().realizedExpense()).isEqualByComparingTo("700.00");
        assertThat(report.cash().netCashGeneration()).isEqualByComparingTo("1100.00");
        assertThat(report.operationalDre().netResult()).isEqualByComparingTo("1000.00");
        assertThat(report.difference()).isEqualByComparingTo("100.00");

        verify(monthlyCash).report(actor, company, 2026);
        verify(operationalDre).report(actor, company, "2026-11");
    }

    private MonthlyCashTotalsService.Report cashReport(
            int targetMonth,
            String realizedIncome,
            String realizedExpense) {

        var months = new ArrayList<MonthlyCashTotalsService.Month>();
        for (int month = 1; month <= 12; month++) {
            months.add(new MonthlyCashTotalsService.Month(
                    month,
                    new BigDecimal(month == targetMonth ? realizedIncome : "0.00"),
                    new BigDecimal(month == targetMonth ? realizedExpense : "0.00"),
                    new BigDecimal("0.00"),
                    new BigDecimal("0.00")));
        }
        return new MonthlyCashTotalsService.Report(2026, months, List.of());
    }

    private OperationalDreMonthlyReadService.Report dreReport(
            String month,
            String revenue,
            String cost) {

        return new OperationalDreMonthlyReadService.Report(
                company,
                month,
                true,
                List.of("FINANCIAL_ENTRIES"),
                List.of(
                        new OperationalDreMonthlyReadService.Line(
                                OperationalDreLine.REC,
                                new BigDecimal(revenue),
                                new BigDecimal("0.00"),
                                new BigDecimal(revenue)),
                        new OperationalDreMonthlyReadService.Line(
                                OperationalDreLine.CUS,
                                new BigDecimal(cost),
                                new BigDecimal("0.00"),
                                new BigDecimal(cost))));
    }
}
