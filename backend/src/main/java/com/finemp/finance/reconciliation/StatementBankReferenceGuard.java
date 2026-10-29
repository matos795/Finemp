package com.finemp.finance.reconciliation;

import com.finemp.finance.bank.BankAccountReferenceGuard;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Reference protection remains active even when statement import is disabled. */
@Component("financialStatementBankReferences")
public class StatementBankReferenceGuard implements BankAccountReferenceGuard {
    private final JdbcTemplate jdbc;
    public StatementBankReferenceGuard(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @Override public boolean hasReferences(UUID company,UUID bank){return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM financial_bank_statement_items WHERE company_id=? AND bank_account_id=? UNION ALL SELECT 1 FROM financial_statement_import_receipts WHERE company_id=? AND bank_account_id=?)",Boolean.class,company,bank,company,bank));}
}
