package com.finemp.integration.questor;

import java.sql.*;

/** Owns a newly opened connection. No commit path is exposed. */
final class QuestorReadOnlySession implements AutoCloseable {
    private final Connection connection;
    private QuestorReadOnlySession(Connection connection) { this.connection = connection; }
    static QuestorReadOnlySession open(QuestorConnectionProvider provider) throws SQLException {
        Connection connection = provider.openConnection();
        try {
            connection.setReadOnly(true);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);
            return new QuestorReadOnlySession(connection);
        } catch (SQLException | RuntimeException failed) {
            try { connection.close(); } catch (SQLException ignored) { }
            throw failed;
        }
    }
    Connection connection() { return connection; }
    @Override public void close() throws SQLException {
        try { connection.rollback(); } finally { connection.close(); }
    }
}
