package com.finemp.finance.reporting;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.finemp.access.CompanyPermission;
import com.finemp.access.CompanyPermissionAuthorizationService;
import com.finemp.administration.OrganizationAccessDeniedException;
import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Compares realized cash with the partial operational DRE for the same month.
 * This is not an accounting reconciliation with Questor.
 */
@Service
@ConditionalOnProperty(
        name = {
                "finemp.finance.monthly-cash.enabled",
                "finemp.finance.operational-dre.enabled"
        },
        havingValue = "true")
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 10)
public class CashVsOperationalDreService {

    private final CompanyPermissionAuthorizationService permissions;
    private final MonthlyCashTotalsService monthlyCash;
    private final OperationalDreMonthlyReadService operationalDre;

    public CashVsOperationalDreService(
            CompanyPermissionAuthorizationService permissions,
            MonthlyCashTotalsService monthlyCash,
            OperationalDreMonthlyReadService operationalDre) {
        this.permissions = permissions;
        this.monthlyCash = monthlyCash;
        this.operationalDre = operationalDre;
    }

    public Report compare(UUID actor, UUID company, String month) {
        requireRead(actor, company);
        YearMonth period = parseMonth(month);

        var cashYear = monthlyCash.report(actor, company, period.getYear());
        var cashMonth = cashYear.months().stream()
                .filter(candidate -> candidate.month() == period.getMonthValue())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Mês ausente no relatório de caixa."));

        var dre = operationalDre.report(actor, company, period.toString());
        if (!period.toString().equals(dre.month())) {
            throw new IllegalStateException("Caixa e DRE retornaram períodos diferentes.");
        }

        BigDecimal netCashGeneration =
                cashMonth.realIncome().subtract(cashMonth.realExpense());
        BigDecimal netResult = dre.subtotals().netResult();
        BigDecimal difference = netCashGeneration.subtract(netResult);

        return new Report(
                company,
                period.toString(),
                true,
                dre.coverage(),
                new Cash(
                        cashMonth.realIncome(),
                        cashMonth.realExpense(),
                        netCashGeneration),
                new OperationalDre(netResult),
                difference);
    }

    private void requireRead(UUID actor, UUID company) {
        if (actor == null
                || company == null
                || !permissions.hasFreshPermission(
                        actor,
                        company,
                        CompanyPermission.FINANCIAL_ENTRY_VIEW)
                    && !permissions.hasFreshPermission(
                        actor,
                        company,
                        CompanyPermission.FINANCIAL_ENTRY_MANAGE)) {
            throw new OrganizationAccessDeniedException();
        }
    }

    private static YearMonth parseMonth(String month) {
        try {
            if (month == null || !month.matches("[0-9]{4}-(0[1-9]|1[0-2])")) {
                throw invalid();
            }
            var period = YearMonth.parse(month);
            if (period.getYear() < 1) {
                throw invalid();
            }
            return period;
        } catch (DateTimeException exception) {
            throw invalid();
        }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Mês da comparação caixa × DRE inválido.");
    }

    public record Cash(
            @JsonFormat(shape = JsonFormat.Shape.STRING)
            BigDecimal realizedIncome,
            @JsonFormat(shape = JsonFormat.Shape.STRING)
            BigDecimal realizedExpense,
            @JsonFormat(shape = JsonFormat.Shape.STRING)
            BigDecimal netCashGeneration) {
    }

    public record OperationalDre(
            @JsonFormat(shape = JsonFormat.Shape.STRING)
            BigDecimal netResult) {
    }

    public record Report(
            UUID companyId,
            String month,
            boolean partial,
            List<String> coverage,
            Cash cash,
            OperationalDre operationalDre,
            @JsonFormat(shape = JsonFormat.Shape.STRING)
            BigDecimal difference) {

        public Report {
            coverage = List.copyOf(coverage);
        }
    }
}
