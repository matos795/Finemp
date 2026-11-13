package com.finemp.finance.reporting;

import com.finemp.access.CompanyPermission;
import com.finemp.access.CompanyPermissionAuthorizationService;
import com.finemp.administration.OrganizationAccessDeniedException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WorkingCapitalCycleServiceTest {

    private final CompanyPermissionAuthorizationService permissions =
            mock(CompanyPermissionAuthorizationService.class);
    private final NamedParameterJdbcTemplate jdbc =
            mock(NamedParameterJdbcTemplate.class);
    private final WorkingCapitalCycleService service =
            new WorkingCapitalCycleService(permissions, jdbc);

    private final UUID actor = UUID.randomUUID();
    private final UUID company = UUID.randomUUID();

    @Test
    void unauthorizedUserNeverQueriesFinancialData() {
        assertThatThrownBy(() -> service.report(
                actor,
                company,
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 30),
                null))
                .isInstanceOf(OrganizationAccessDeniedException.class);

        verifyNoInteractions(jdbc);
    }

    @Test
    void invalidPeriodOrInventoryDaysNeverQueriesFinancialData() {
        when(permissions.hasFreshPermission(
                actor,
                company,
                CompanyPermission.FINANCIAL_ENTRY_VIEW))
                .thenReturn(true);

        assertThatThrownBy(() -> service.report(
                actor,
                company,
                LocalDate.of(2026, 12, 1),
                LocalDate.of(2026, 11, 30),
                null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> service.report(
                actor,
                company,
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 30),
                new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(jdbc);
    }

    @Test
    void reportUsesInclusiveCompanyScopedDocumentPeriod() {
        when(permissions.hasFreshPermission(
                actor,
                company,
                CompanyPermission.FINANCIAL_ENTRY_VIEW))
                .thenReturn(true);

        service.report(
                actor,
                company,
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 30),
                new BigDecimal("7.00"));

        verify(jdbc).query(
                argThat((String sql) ->
                        sql.contains("company_id=:company")
                                && sql.contains("document_date BETWEEN :from AND :to")),
                argThat((org.springframework.jdbc.core.namedparam.SqlParameterSource params) ->
                        company.equals(params.getValue("company"))
                                && LocalDate.of(2026, 11, 1).equals(params.getValue("from"))
                                && LocalDate.of(2026, 11, 30).equals(params.getValue("to"))),
                any(org.springframework.jdbc.core.RowCallbackHandler.class));
    }
}
