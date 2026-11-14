package com.finemp.debt;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.finemp.access.CompanyPermission;
import com.finemp.access.CompanyPermissionAuthorizationService;
import com.finemp.administration.OrganizationAccessDeniedException;
import com.finemp.administration.PagedResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 5)
public class DebtPortfolioReadService {

    private static final int MAX_PAGE_SIZE = 100;

    private final CompanyPermissionAuthorizationService permissions;
    private final NamedParameterJdbcTemplate jdbc;

    public DebtPortfolioReadService(
            CompanyPermissionAuthorizationService permissions,
            NamedParameterJdbcTemplate jdbc) {
        this.permissions = permissions;
        this.jdbc = jdbc;
    }

    public Overview overview(UUID actor, UUID companyId) {
        requireRead(actor, companyId);

        var params = new MapSqlParameterSource("company", companyId);
        var rows = jdbc.query(
                """
                SELECT a.origin,
                       count(*) AS agreement_count,
                       coalesce(sum(a.principal_amount), 0) AS principal_amount,
                       coalesce(sum(coalesce(s.scheduled_amount, 0)), 0) AS scheduled_amount,
                       coalesce(sum(coalesce(s.interest_amount, 0)), 0) AS interest_amount
                  FROM debt_agreements a
                  LEFT JOIN (
                        SELECT debt_agreement_id,
                               sum(installment_amount) AS scheduled_amount,
                               sum(interest_amount) AS interest_amount
                          FROM debt_installments
                         GROUP BY debt_agreement_id
                  ) s ON s.debt_agreement_id = a.id
                 WHERE a.company_id = :company
                 GROUP BY a.origin
                """,
                params,
                (row, index) -> new OriginSummary(
                        DebtOrigin.valueOf(row.getString("origin")),
                        row.getLong("agreement_count"),
                        row.getBigDecimal("principal_amount"),
                        row.getBigDecimal("scheduled_amount"),
                        row.getBigDecimal("interest_amount")));

        var indexed = new EnumMap<DebtOrigin, OriginSummary>(DebtOrigin.class);
        rows.forEach(row -> indexed.put(row.origin(), row));

        var byOrigin = Arrays.stream(DebtOrigin.values())
                .map(origin -> indexed.getOrDefault(
                        origin,
                        new OriginSummary(
                                origin,
                                0,
                                BigDecimal.ZERO.setScale(2),
                                BigDecimal.ZERO.setScale(2),
                                BigDecimal.ZERO.setScale(2))))
                .toList();

        long agreementCount = byOrigin.stream()
                .mapToLong(OriginSummary::agreementCount)
                .sum();
        BigDecimal principal = sum(byOrigin, OriginSummary::principalAmount);
        BigDecimal scheduled = sum(byOrigin, OriginSummary::scheduledAmount);
        BigDecimal interest = sum(byOrigin, OriginSummary::interestAmount);

        return new Overview(
                companyId,
                agreementCount,
                principal,
                scheduled,
                interest,
                byOrigin);
    }

    public PagedResponse<ScheduleItem> schedule(
            UUID actor,
            UUID companyId,
            String month,
            int page,
            int size) {
        requireRead(actor, companyId);
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) throw invalid();

        YearMonth competence;
        try {
            competence = YearMonth.parse(month);
        } catch (RuntimeException exception) {
            throw invalid();
        }

        LocalDate from = competence.atDay(1);
        LocalDate to = competence.atEndOfMonth();
        var params = new MapSqlParameterSource()
                .addValue("company", companyId)
                .addValue("from", from)
                .addValue("to", to)
                .addValue("limit", size)
                .addValue("offset", (long) page * size);

        Long total = jdbc.queryForObject(
                """
                SELECT count(*)
                  FROM debt_installments i
                  JOIN debt_agreements a ON a.id = i.debt_agreement_id
                 WHERE a.company_id = :company
                   AND i.due_date BETWEEN :from AND :to
                """,
                params,
                Long.class);

        var items = jdbc.query(
                """
                SELECT i.id,
                       a.id AS debt_id,
                       a.origin,
                       a.description,
                       CASE
                           WHEN p.id IS NULL THEN NULL
                           WHEN trim(p.trade_name) <> '' THEN p.trade_name
                           ELSE p.name
                       END AS creditor_name,
                       i.installment_number,
                       i.due_date,
                       i.amortization_amount,
                       i.interest_amount,
                       i.installment_amount,
                       i.balance_after
                  FROM debt_installments i
                  JOIN debt_agreements a ON a.id = i.debt_agreement_id
                  LEFT JOIN business_partners p ON p.id = a.business_partner_id
                 WHERE a.company_id = :company
                   AND i.due_date BETWEEN :from AND :to
                 ORDER BY i.due_date, a.origin, a.description, i.installment_number, i.id
                 LIMIT :limit OFFSET :offset
                """,
                params,
                (row, index) -> new ScheduleItem(
                        row.getObject("id", UUID.class),
                        row.getObject("debt_id", UUID.class),
                        DebtOrigin.valueOf(row.getString("origin")),
                        row.getString("description"),
                        row.getString("creditor_name"),
                        row.getInt("installment_number"),
                        row.getObject("due_date", LocalDate.class),
                        row.getBigDecimal("amortization_amount"),
                        row.getBigDecimal("interest_amount"),
                        row.getBigDecimal("installment_amount"),
                        row.getBigDecimal("balance_after")));

        long totalItems = total == null ? 0 : total;
        int totalPages = totalItems == 0
                ? 0
                : (int) ((totalItems + size - 1) / size);

        return new PagedResponse<>(
                items,
                page,
                size,
                totalItems,
                totalPages);
    }

    private void requireRead(UUID actor, UUID companyId) {
        if (actor == null
                || companyId == null
                || (!permissions.hasFreshPermission(
                            actor,
                            companyId,
                            CompanyPermission.DEBT_VIEW)
                    && !permissions.hasFreshPermission(
                            actor,
                            companyId,
                            CompanyPermission.DEBT_MANAGE))) {
            throw new OrganizationAccessDeniedException();
        }
    }

    private static BigDecimal sum(
            List<OriginSummary> rows,
            java.util.function.Function<OriginSummary, BigDecimal> mapper) {
        return rows.stream()
                .map(mapper)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Parâmetros do portfólio de dívidas inválidos.");
    }

    public record OriginSummary(
            DebtOrigin origin,
            long agreementCount,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal principalAmount,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal scheduledAmount,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal interestAmount) {
    }

    public record Overview(
            UUID companyId,
            long agreementCount,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal principalAmount,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal scheduledAmount,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal interestAmount,
            List<OriginSummary> byOrigin) {
        public Overview {
            byOrigin = List.copyOf(byOrigin);
        }
    }

    public record ScheduleItem(
            UUID id,
            UUID debtId,
            DebtOrigin origin,
            String description,
            String creditorName,
            int installmentNumber,
            LocalDate dueDate,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amortizationAmount,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal interestAmount,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal installmentAmount,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal balanceAfter) {
    }
}
