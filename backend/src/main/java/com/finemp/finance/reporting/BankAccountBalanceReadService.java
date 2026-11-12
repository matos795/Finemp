package com.finemp.finance.reporting;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.finemp.access.CompanyPermission;
import com.finemp.access.CompanyPermissionAuthorizationService;
import com.finemp.administration.OrganizationAccessDeniedException;
import com.finemp.finance.bank.BankAccountType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix="finemp.finance.account-balances",name="enabled",havingValue="true")
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,timeout=5)
public class BankAccountBalanceReadService {
    private final CompanyPermissionAuthorizationService permissions;
    private final NamedParameterJdbcTemplate jdbc;

    public BankAccountBalanceReadService(CompanyPermissionAuthorizationService permissions,NamedParameterJdbcTemplate jdbc) {
        this.permissions=permissions;
        this.jdbc=jdbc;
    }

    public Report report(UUID actor,UUID company) {
        requireAccess(actor,company);
        var params=new MapSqlParameterSource("company",company);
        String sql="""
            WITH account_deltas AS (
                SELECT bank_account_id,
                       SUM(CASE
                           WHEN direction='INCOME' THEN COALESCE(actual_paid_amount,nominal_amount)
                           WHEN direction='EXPENSE' THEN -COALESCE(actual_paid_amount,nominal_amount)
                           ELSE 0
                       END) AS delta
                FROM financial_entries
                WHERE company_id=:company AND payment_date IS NOT NULL
                GROUP BY bank_account_id

                UNION ALL

                SELECT destination_bank_account_id AS bank_account_id,SUM(amount) AS delta
                FROM financial_bank_transfers
                WHERE company_id=:company
                GROUP BY destination_bank_account_id

                UNION ALL

                SELECT origin_bank_account_id AS bank_account_id,-SUM(amount) AS delta
                FROM financial_bank_transfers
                WHERE company_id=:company
                GROUP BY origin_bank_account_id
            ),
            balance_deltas AS (
                SELECT bank_account_id,SUM(delta) AS delta
                FROM account_deltas
                GROUP BY bank_account_id
            )
            SELECT b.id,b.name,b.type,b.active,b.opening_balance,
                   b.opening_balance + COALESCE(d.delta,0) AS current_balance
            FROM financial_bank_accounts b
            LEFT JOIN balance_deltas d ON d.bank_account_id=b.id
            WHERE b.company_id=:company
            ORDER BY lower(b.name),b.id
            """;
        List<AccountBalance> accounts=jdbc.query(sql,params,(rs,row)->new AccountBalance(
                rs.getObject("id",UUID.class),
                rs.getString("name"),
                BankAccountType.valueOf(rs.getString("type")),
                rs.getBoolean("active"),
                money(rs.getBigDecimal("opening_balance")),
                money(rs.getBigDecimal("current_balance"))));
        return new Report(accounts);
    }

    private void requireAccess(UUID actor,UUID company) {
        if(actor==null || company==null)throw new OrganizationAccessDeniedException();
        boolean bank=permissions.hasFreshPermission(actor,company,CompanyPermission.BANK_ACCOUNT_VIEW)
                || permissions.hasFreshPermission(actor,company,CompanyPermission.BANK_ACCOUNT_MANAGE);
        boolean entries=permissions.hasFreshPermission(actor,company,CompanyPermission.FINANCIAL_ENTRY_VIEW)
                || permissions.hasFreshPermission(actor,company,CompanyPermission.FINANCIAL_ENTRY_MANAGE);
        if(!bank || !entries)throw new OrganizationAccessDeniedException();
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2,RoundingMode.UNNECESSARY);
    }

    public record AccountBalance(
            UUID id,
            String name,
            BankAccountType type,
            boolean active,
            @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal openingBalance,
            @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal currentBalance) {}

    public record Report(List<AccountBalance> accounts) {
        public Report { accounts=List.copyOf(accounts); }
    }
}
