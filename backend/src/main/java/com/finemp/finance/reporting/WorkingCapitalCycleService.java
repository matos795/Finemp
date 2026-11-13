package com.finemp.finance.reporting;

import com.finemp.access.*;
import com.finemp.administration.OrganizationAccessDeniedException;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 5)
public class WorkingCapitalCycleService {

    private static final Set<Integer> ALLOWED_MONTHS = Set.of(1, 3, 6, 12);

    private final CompanyPermissionAuthorizationService permissions;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock calendar;

    public WorkingCapitalCycleService(
            CompanyPermissionAuthorizationService permissions,
            NamedParameterJdbcTemplate jdbc,
            @Value("${finemp.finance.entries.calendar-zone:America/Sao_Paulo}") String calendarZone) {
        this.permissions = permissions;
        this.jdbc = jdbc;
        this.calendar = Clock.system(ZoneId.of(calendarZone));
    }

    public WorkingCapitalCycleCalculator.Report report(
            UUID actor,
            UUID company,
            int months,
            int pmeDays) {

        requireRead(actor, company);
        if (!ALLOWED_MONTHS.contains(months) || pmeDays < 0) {
            throw new IllegalArgumentException("Parâmetros do ciclo financeiro inválidos.");
        }

        LocalDate today = LocalDate.now(calendar);
        LocalDate from = today.withDayOfMonth(1).minusMonths(months - 1L);

        var params = new MapSqlParameterSource()
                .addValue("company", company)
                .addValue("from", from)
                .addValue("to", today);

        String effectiveDre = "coalesce(category.dre_line,parent_category.dre_line,'NAO')";
        String effectiveActivity = "coalesce(category.cash_activity,parent_category.cash_activity,'OPERATING')";

        String sql = """
                SELECT
                    CASE
                        WHEN entry.direction='INCOME' THEN 'RECEIVABLE'
                        ELSE 'PAYABLE'
                    END AS side,
                    entry.partner_id,
                    CASE
                        WHEN trim(coalesce(partner.trade_name,''))<>'' THEN partner.trade_name
                        ELSE coalesce(partner.name,'')
                    END AS partner_name,
                    entry.nominal_amount,
                    coalesce(entry.actual_paid_amount,entry.nominal_amount) AS effective_amount,
                    entry.document_date,
                    entry.due_date,
                    entry.payment_date
                FROM financial_entries entry
                JOIN financial_categories category
                  ON category.id=entry.category_id
                 AND category.company_id=entry.company_id
                LEFT JOIN financial_categories parent_category
                  ON parent_category.id=category.parent_id
                 AND parent_category.company_id=entry.company_id
                LEFT JOIN business_partners partner
                  ON partner.id=entry.partner_id
                 AND partner.company_id=entry.company_id
                WHERE entry.company_id=:company
                  AND (
                    (entry.direction='INCOME' AND %s='REC')
                    OR
                    (entry.direction='EXPENSE'
                     AND %s='OPERATING'
                     AND %s IN ('CUS','DCOM','DADM','OUT'))
                  )
                  AND (
                    entry.document_date BETWEEN :from AND :to
                    OR entry.payment_date BETWEEN :from AND :to
                    OR entry.payment_date IS NULL
                    OR entry.payment_date > :to
                  )
                """.formatted(effectiveDre, effectiveActivity, effectiveDre);

        List<WorkingCapitalCycleCalculator.Row> rows = jdbc.query(
                sql,
                params,
                (rs, rowNum) -> new WorkingCapitalCycleCalculator.Row(
                        WorkingCapitalCycleCalculator.Side.valueOf(rs.getString("side")),
                        rs.getObject("partner_id") == null
                                ? null
                                : rs.getObject("partner_id", UUID.class).toString(),
                        rs.getString("partner_name"),
                        rs.getBigDecimal("nominal_amount"),
                        rs.getBigDecimal("effective_amount"),
                        rs.getObject("document_date", LocalDate.class),
                        rs.getObject("due_date", LocalDate.class),
                        rs.getObject("payment_date", LocalDate.class)));

        return WorkingCapitalCycleCalculator.calculate(
                from,
                today,
                today,
                pmeDays,
                rows);
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
}
