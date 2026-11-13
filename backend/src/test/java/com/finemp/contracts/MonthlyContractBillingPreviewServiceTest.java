package com.finemp.contracts;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.finemp.access.CompanyPermission;
import com.finemp.access.CompanyPermissionAuthorizationService;
import com.finemp.administration.OrganizationAccessDeniedException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

class MonthlyContractBillingPreviewServiceTest {

    private final ContractRepository contracts = mock(ContractRepository.class);
    private final CompanyPermissionAuthorizationService permissions =
            mock(CompanyPermissionAuthorizationService.class);
    private final MonthlyContractBillingPreviewService service =
            new MonthlyContractBillingPreviewService(contracts, permissions);

    private final UUID actor = UUID.randomUUID();
    private final UUID company = UUID.randomUUID();

    @Test
    void unauthorizedUserNeverReadsContractBillingData() {
        assertThatThrownBy(() -> service.report(
                actor,
                company,
                YearMonth.of(2026, 11),
                0,
                50))
                .isInstanceOf(OrganizationAccessDeniedException.class);

        verifyNoInteractions(contracts);
    }

    @Test
    void previewUsesCompanyAndInclusiveMonthBoundaries() {
        when(permissions.hasFreshPermission(actor, company, CompanyPermission.CONTRACT_VIEW))
                .thenReturn(true);
        when(contracts.monthlyPreview(eq(company), any(), any(), any(Pageable.class)))
                .thenReturn(Page.empty());
        when(contracts.monthlyPreviewTotal(eq(company), any(), any(), any()))
                .thenReturn(BigDecimal.ZERO);

        var result = service.report(
                actor,
                company,
                YearMonth.of(2026, 2),
                0,
                50);

        assertThat(result.month()).isEqualTo("2026-02");
        assertThat(result.incomeTotal()).isEqualByComparingTo("0.00");
        assertThat(result.expenseTotal()).isEqualByComparingTo("0.00");
        assertThat(result.netTotal()).isEqualByComparingTo("0.00");

        verify(contracts).monthlyPreview(
                eq(company),
                eq(LocalDate.of(2026, 2, 1)),
                eq(LocalDate.of(2026, 2, 28)),
                any(Pageable.class));
        verify(contracts).monthlyPreviewTotal(
                company,
                ContractDirection.INCOME,
                LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 2, 28));
        verify(contracts).monthlyPreviewTotal(
                company,
                ContractDirection.EXPENSE,
                LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 2, 28));
    }
}
