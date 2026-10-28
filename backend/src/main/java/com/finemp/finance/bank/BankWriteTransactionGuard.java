package com.finemp.finance.bank;

import java.sql.Connection;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
@ConditionalOnProperty(prefix="finemp.finance.catalog",name="enabled",havingValue="true")
public class BankWriteTransactionGuard{
    private final JdbcTemplate jdbc;
    public BankWriteTransactionGuard(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @Transactional(propagation=Propagation.MANDATORY)
    public void requireReadCommitted(){
        boolean supported=Boolean.TRUE.equals(jdbc.execute((ConnectionCallback<Boolean>)connection->connection.getTransactionIsolation()==Connection.TRANSACTION_READ_COMMITTED));
        if(!supported)throw new BankAccountConflictException(BankAccountConflictException.Reason.TRANSACTION_NOT_SUPPORTED);
        jdbc.execute("SET LOCAL lock_timeout = '5s'");
    }
}
