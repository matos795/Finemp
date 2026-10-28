package com.finemp.finance.entry;

import java.sql.Connection;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
@ConditionalOnProperty(prefix="finemp.finance.entries",name="enabled",havingValue="true")
public class EntryWriteTransactionGuard {
    private final JdbcTemplate jdbc;
    public EntryWriteTransactionGuard(JdbcTemplate jdbc) {this.jdbc=jdbc;}
    @Transactional(propagation=Propagation.MANDATORY)
    public void requireReadCommitted() {
        if(!Boolean.TRUE.equals(jdbc.execute((ConnectionCallback<Boolean>)connection->connection.getTransactionIsolation()==Connection.TRANSACTION_READ_COMMITTED)))
            throw new FinancialEntryCommandConflictException(FinancialEntryCommandConflictException.Reason.TRANSACTION_NOT_SUPPORTED);
        jdbc.execute("SET LOCAL lock_timeout = '5s'");
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public Optional<Long> lockEntryVersion(UUID company,UUID entry) {
        return jdbc.query("SELECT version FROM financial_entries WHERE company_id=? AND id=? FOR UPDATE",
                (row,index)->row.getLong("version"),company,entry).stream().findFirst();
    }
}
