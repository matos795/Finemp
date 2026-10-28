package com.finemp.finance.transfer;

import java.sql.Connection;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
@ConditionalOnProperty(prefix="finemp.finance.transfers",name="enabled",havingValue="true")
@Transactional(propagation=Propagation.MANDATORY)
public class BankTransferWriteGuard {
    private final JdbcTemplate jdbc;
    public BankTransferWriteGuard(JdbcTemplate jdbc) {this.jdbc=jdbc;}
    public void requireReadCommitted() {
        if(!Boolean.TRUE.equals(jdbc.execute((ConnectionCallback<Boolean>)connection->connection.getTransactionIsolation()==Connection.TRANSACTION_READ_COMMITTED)))
            throw new BankTransferCommandConflictException(BankTransferCommandConflictException.Reason.TRANSACTION_NOT_SUPPORTED);
        jdbc.execute("SET LOCAL lock_timeout = '5s'");
    }
    public void validateCreationReferences(UUID company,BankTransferValues values) {
        // Company lock serializes this snapshot with the catalog writers. No blanket inactive-selection veto.
        var state=jdbc.queryForObject("""
                SELECT count(*) FILTER(WHERE active) AS active_count,
                       count(*) FILTER(WHERE id IN (?,?)) AS selected_count
                FROM financial_bank_accounts WHERE company_id=?
                """,(row,index)->new long[]{row.getLong("active_count"),row.getLong("selected_count")},
                values.originBankAccountId(),values.destinationBankAccountId(),company);
        if(state==null || state[1]!=2)throw new IllegalArgumentException("Referências de transferência inválidas.");
        if(state[0]<2)throw new BankTransferCommandConflictException(BankTransferCommandConflictException.Reason.INSUFFICIENT_ACTIVE_ACCOUNTS);
    }
    public Optional<Long> lockVersion(UUID company,UUID transfer) {
        return jdbc.query("SELECT version FROM financial_bank_transfers WHERE company_id=? AND id=? FOR UPDATE",
                (row,index)->row.getLong("version"),company,transfer).stream().findFirst();
    }
}
