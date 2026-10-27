package com.finemp.accounting.sync.control;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.sql.Connection;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

class SyncControlTransactionGuardTest {
    @Test void shouldRejectOuterRepeatableReadBeforeAttemptingAnyLock() throws Exception {
        var jdbc = jdbc(Connection.TRANSACTION_REPEATABLE_READ);
        assertThatThrownBy(() -> new SyncControlTransactionGuard(jdbc).requireReadCommitted()).isInstanceOf(IllegalStateException.class);
        verify(jdbc, never()).execute(anyString());
    }
    @Test void shouldAcceptReadCommittedAndBoundLocalLockWaitWithoutChangingGlobalDatabaseConfiguration() throws Exception {
        var jdbc = jdbc(Connection.TRANSACTION_READ_COMMITTED);
        new SyncControlTransactionGuard(jdbc).requireReadCommitted();
        verify(jdbc).execute("SET LOCAL lock_timeout = '5s'");
    }
    @SuppressWarnings("unchecked")
    private JdbcTemplate jdbc(int isolation) throws Exception {
        var connection = mock(Connection.class);
        when(connection.getTransactionIsolation()).thenReturn(isolation);
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.execute(any(ConnectionCallback.class))).thenAnswer(invocation ->
                ((ConnectionCallback<?>) invocation.getArgument(0)).doInConnection(connection));
        return jdbc;
    }
}

