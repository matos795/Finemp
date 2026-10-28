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
    private final com.finemp.finance.period.FinancialPeriodGuard periods;
    public EntryWriteTransactionGuard(JdbcTemplate jdbc,com.finemp.finance.period.FinancialPeriodGuard periods) {this.jdbc=jdbc;this.periods=periods;}
    @Transactional(propagation=Propagation.MANDATORY)
    public void requireEntryPeriodsOpen(UUID company,FinancialEntryValues... states) {requirePeriods(company,true,states);}
    @Transactional(propagation=Propagation.MANDATORY)
    public void requirePaymentPeriodsOpen(UUID company,FinancialEntryValues... states) {requirePeriods(company,false,states);}
    private void requirePeriods(UUID company,boolean competence,FinancialEntryValues... states) {
        var affected=new HashSet<java.time.YearMonth>();
        for(var state:states) {
            if(competence)affected.add(state.competence());
            if(state.paymentDate()!=null)affected.add(java.time.YearMonth.from(state.paymentDate()));
        }
        periods.requireOpen(company,affected);
    }
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
