package com.finemp.finance.reporting;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.finemp.access.CompanyPermission;
import com.finemp.access.CompanyPermissionAuthorizationService;
import com.finemp.administration.OrganizationAccessDeniedException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Actual received income grouped by the payment method persisted on the
 * financial entry. This is cash realization, not invoicing or a payment-method
 * catalog.
 */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 5)
public class ReceivedPaymentsByMethodService {

    private static final String UNSPECIFIED_METHOD = "Não informado";

    private final CompanyPermissionAuthorizationService permissions;
    private final NamedParameterJdbcTemplate jdbc;

    public ReceivedPaymentsByMethodService(
            CompanyPermissionAuthorizationService permissions,
            NamedParameterJdbcTemplate jdbc) {
        this.permissions = permissions;
        this.jdbc = jdbc;
    }

    public Report report(UUID actor, UUID company, LocalDate from, LocalDate to) {
        requireRead(actor, company);
        if (from == null || to == null || to.isBefore(from)) {
            throw new IllegalArgumentException("Período de recebimentos inválido.");
        }

        var params = new MapSqlParameterSource()
                .addValue("company", company)
                .addValue("from", from)
                .addValue("to", to);

        String sql = """
                SELECT
                    CASE
                        WHEN trim(payment_method) = '' THEN :unspecified
                        ELSE payment_method
                    END AS payment_method,
                    count(*) AS payment_count,
                    sum(COALESCE(actual_paid_amount, nominal_amount)) AS total_amount
                FROM financial_entries
                WHERE company_id=:company
                  AND direction='INCOME'
                  AND payment_date IS NOT NULL
                  AND payment_date BETWEEN :from AND :to
                GROUP BY 1
                ORDER BY total_amount DESC, payment_method ASC
                """;

        params.addValue("unspecified", UNSPECIFIED_METHOD);

        var methods = new ArrayList<MethodTotal>();
        jdbc.query(sql, params, rs -> methods.add(new MethodTotal(
                rs.getString("payment_method"),
                rs.getLong("payment_count"),
                money(rs.getBigDecimal("total_amount")))));

        BigDecimal totalAmount = methods.stream()
                .map(MethodTotal::totalAmount)
                .reduce(zero(), BigDecimal::add);
        long totalCount = methods.stream()
                .mapToLong(MethodTotal::paymentCount)
                .sum();

        return new Report(
                from,
                to,
                totalCount,
                totalAmount,
                methods);
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

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2);
    }

    private static BigDecimal money(BigDecimal value) {
        if (value == null) {
            return zero();
        }
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    public record MethodTotal(
            String paymentMethod,
            long paymentCount,
            @JsonFormat(shape = JsonFormat.Shape.STRING)
            BigDecimal totalAmount) {
    }

    public record Report(
            LocalDate from,
            LocalDate to,
            long totalCount,
            @JsonFormat(shape = JsonFormat.Shape.STRING)
            BigDecimal totalAmount,
            List<MethodTotal> methods) {

        public Report {
            methods = List.copyOf(methods);
        }
    }
}
