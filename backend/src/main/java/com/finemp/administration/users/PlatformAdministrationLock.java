package com.finemp.administration.users;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import java.sql.Connection;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PlatformAdministrationLock {

    private final JdbcTemplate jdbc;

    public PlatformAdministrationLock(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(propagation = Propagation.MANDATORY)
    public void acquire() {
        // Stable namespace for platform-user administration, shared across replicas.
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            if (connection.getTransactionIsolation() != Connection.TRANSACTION_READ_COMMITTED) {
                throw new IllegalStateException("Platform administration requires READ_COMMITTED isolation.");
            }
            try (var statement = connection.prepareStatement("SELECT pg_advisory_xact_lock(1179209285, 1431520594)")) {
                statement.setQueryTimeout(10);
                statement.execute();
            }
            return null;
        });
    }
}
