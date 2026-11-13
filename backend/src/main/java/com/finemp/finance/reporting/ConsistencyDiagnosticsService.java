package com.finemp.finance.reporting;

import com.finemp.access.CompanyPermission;
import com.finemp.access.CompanyPermissionAuthorizationService;
import com.finemp.administration.OrganizationAccessDeniedException;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 5)
public class ConsistencyDiagnosticsService {

    private static final int SAMPLE_LIMIT = 50;

    private final CompanyPermissionAuthorizationService permissions;
    private final NamedParameterJdbcTemplate jdbc;

    public ConsistencyDiagnosticsService(
            CompanyPermissionAuthorizationService permissions,
            NamedParameterJdbcTemplate jdbc) {
        this.permissions = permissions;
        this.jdbc = jdbc;
    }

    public Report report(UUID actor, UUID companyId) {
        requireRead(actor, companyId);

        var checks = List.of(
                check(
                        companyId,
                        "CONTRACT_PARTNER_COMPANY",
                        "Contratos com parceiro de outra empresa",
                        """
                        FROM contracts c
                        JOIN business_partners p ON p.id = c.business_partner_id
                        WHERE c.company_id = :company
                          AND p.company_id <> c.company_id
                        """,
                        "c.id"),
                check(
                        companyId,
                        "SERVICE_INVOICE_PARTNER_COMPANY",
                        "Notas com parceiro de outra empresa",
                        """
                        FROM service_invoice_drafts n
                        JOIN business_partners p ON p.id = n.business_partner_id
                        WHERE n.company_id = :company
                          AND p.company_id <> n.company_id
                        """,
                        "n.id"),
                check(
                        companyId,
                        "SERVICE_INVOICE_CONTRACT_COMPANY",
                        "Notas vinculadas a contrato de outra empresa",
                        """
                        FROM service_invoice_drafts n
                        JOIN contracts c ON c.id = n.contract_id
                        WHERE n.company_id = :company
                          AND n.contract_id IS NOT NULL
                          AND c.company_id <> n.company_id
                        """,
                        "n.id"),
                check(
                        companyId,
                        "SERVICE_INVOICE_CONTRACT_PARTNER",
                        "Notas cujo parceiro difere do contrato vinculado",
                        """
                        FROM service_invoice_drafts n
                        JOIN contracts c ON c.id = n.contract_id
                        WHERE n.company_id = :company
                          AND n.contract_id IS NOT NULL
                          AND n.business_partner_id <> c.business_partner_id
                        """,
                        "n.id"));

        long issueCount = checks.stream()
                .mapToLong(Check::occurrenceCount)
                .sum();

        return new Report(
                companyId,
                true,
                List.of("CONTRACTS", "SERVICE_INVOICES", "REFERENTIAL_LINKS"),
                issueCount,
                checks);
    }

    private Check check(
            UUID companyId,
            String code,
            String label,
            String fromWhere,
            String idExpression) {
        var params = new MapSqlParameterSource("company", companyId);

        Long count = jdbc.queryForObject(
                "SELECT count(*) " + fromWhere,
                params,
                Long.class);

        var ids = jdbc.query(
                "SELECT " + idExpression + " AS occurrence_id " + fromWhere
                        + " ORDER BY occurrence_id LIMIT " + SAMPLE_LIMIT,
                params,
                (row, index) -> row.getObject("occurrence_id", UUID.class));

        return new Check(
                code,
                label,
                count != null && count > 0 ? "ISSUE" : "OK",
                count == null ? 0 : count,
                ids);
    }

    private void requireRead(UUID actor, UUID companyId) {
        boolean contracts = hasAny(
                actor,
                companyId,
                CompanyPermission.CONTRACT_VIEW,
                CompanyPermission.CONTRACT_MANAGE);
        boolean billing = hasAny(
                actor,
                companyId,
                CompanyPermission.BILLING_VIEW,
                CompanyPermission.BILLING_MANAGE);

        if (!contracts || !billing) {
            throw new OrganizationAccessDeniedException();
        }
    }

    private boolean hasAny(
            UUID actor,
            UUID companyId,
            CompanyPermission first,
            CompanyPermission second) {
        return actor != null
                && companyId != null
                && (permissions.hasFreshPermission(actor, companyId, first)
                    || permissions.hasFreshPermission(actor, companyId, second));
    }

    public record Check(
            String code,
            String label,
            String status,
            long occurrenceCount,
            List<UUID> occurrenceIds) {
        public Check {
            occurrenceIds = List.copyOf(occurrenceIds);
        }
    }

    public record Report(
            UUID companyId,
            boolean partial,
            List<String> coverage,
            long issueCount,
            List<Check> checks) {
        public Report {
            coverage = List.copyOf(coverage);
            checks = List.copyOf(checks);
        }
    }
}
