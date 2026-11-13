package com.finemp.finance.reporting;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.finemp.access.CompanyPermission;
import com.finemp.access.CompanyPermissionAuthorizationService;
import com.finemp.administration.OrganizationAccessDeniedException;
import com.finemp.finance.category.FinancialDirection;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.UUID;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 5)
public class WorkingCapitalCycleService {

    private final CompanyPermissionAuthorizationService permissions;
    private final NamedParameterJdbcTemplate jdbc;

    public WorkingCapitalCycleService(
            CompanyPermissionAuthorizationService permissions,
            NamedParameterJdbcTemplate jdbc) {
        this.permissions = permissions;
        this.jdbc = jdbc;
    }

    public Report report(
            UUID actor,
            UUID company,
            LocalDate from,
            LocalDate to,
            BigDecimal inventoryDays) {
        requireRead(actor, company);
        validate(from, to, inventoryDays);

        var params = new MapSqlParameterSource()
                .addValue("company", company)
                .addValue("from", from)
                .addValue("to", to);

        var entries = new ArrayList<WorkingCapitalCycleCalculator.Entry>();
        RowCallbackHandler collect = rs -> entries.add(
                new WorkingCapitalCycleCalculator.Entry(
                        FinancialDirection.valueOf(rs.getString("direction")),
                        rs.getBigDecimal("nominal_amount"),
                        rs.getObject("document_date", LocalDate.class),
                        rs.getObject("due_date", LocalDate.class),
                        rs.getObject("payment_date", LocalDate.class)));

        jdbc.query("""
                SELECT direction, nominal_amount, document_date, due_date, payment_date
                FROM financial_entries
                WHERE company_id=:company
                  AND document_date BETWEEN :from AND :to
                ORDER BY document_date ASC, id ASC
                """,
                params,
                collect);

        int periodDays = Math.toIntExact(ChronoUnit.DAYS.between(from, to) + 1);
        var calculated = WorkingCapitalCycleCalculator.calculate(
                entries,
                inventoryDays,
                periodDays);

        long receivableTitles = entries.stream()
                .filter(entry -> entry.direction() == FinancialDirection.INCOME)
                .count();
        long payableTitles = entries.stream()
                .filter(entry -> entry.direction() == FinancialDirection.EXPENSE)
                .count();

        return new Report(
                from,
                to,
                periodDays,
                receivableTitles,
                payableTitles,
                calculated.receivablesDays(),
                calculated.payablesDays(),
                calculated.inventoryDays(),
                calculated.operatingCycleDays(),
                calculated.financialCycleDays(),
                calculated.dailyRevenue(),
                calculated.cycleWorkingCapital());
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

    private static void validate(
            LocalDate from,
            LocalDate to,
            BigDecimal inventoryDays) {
        if (from == null
                || to == null
                || to.isBefore(from)
                || from.getYear() < 1
                || to.getYear() > 9999
                || ChronoUnit.DAYS.between(from, to) > 3660
                || inventoryDays != null
                    && (inventoryDays.signum() < 0
                        || inventoryDays.compareTo(new BigDecimal("3650")) > 0
                        || inventoryDays.scale() > 2)) {
            throw new IllegalArgumentException("Parâmetros do ciclo financeiro inválidos.");
        }
    }

    public record Report(
            LocalDate from,
            LocalDate to,
            int periodDays,
            long receivableTitles,
            long payableTitles,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal receivablesDays,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal payablesDays,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal inventoryDays,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal operatingCycleDays,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal financialCycleDays,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal dailyRevenue,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal cycleWorkingCapital) {
    }
}
