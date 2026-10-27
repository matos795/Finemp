package com.finemp.accounting.sync.control;

import java.sql.Connection;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnExpression("${finemp.sync.control.enabled:false} || ${finemp.sync.executor.enabled:false} || ${finemp.connector.enabled:false}")
public class SyncControlTransactionGuard {
    private final JdbcTemplate jdbc;
    public SyncControlTransactionGuard(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(propagation = Propagation.MANDATORY)
    public void requireReadCommitted() {
        boolean compatible = Boolean.TRUE.equals(jdbc.execute((ConnectionCallback<Boolean>) connection ->
                connection.getTransactionIsolation() == Connection.TRANSACTION_READ_COMMITTED));
        if (!compatible) throw new IllegalStateException("Sync mutations require READ_COMMITTED isolation.");
        jdbc.execute("SET LOCAL lock_timeout = '5s'");
    }
}
