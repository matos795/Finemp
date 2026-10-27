package com.finemp.integration.questor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.finemp.accounting.reading.AccountingReadLimitExceededException;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class FirebirdQuestorReadLimitTest {
    @Test void shouldReturnTheCompleteReadWhenItFitsAndRequestOneExtraRowOnlyToDetectOverflow() throws Exception {
        var fixture = fixture(false);
        var result = new FirebirdQuestorAccountingReader(fixture.provider(), 1)
                .readMovements("1", LocalDate.of(2026,7,1), LocalDate.of(2026,7,31));
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().amount()).isEqualByComparingTo("1.2500");
        verify(fixture.statement()).setMaxRows(2);
    }
    @Test void shouldNeverReturnTruncatedMovementsAndAlwaysCloseExternalResourcesOnOverflow() throws Exception {
        var fixture = fixture(true);
        assertThatThrownBy(() -> new FirebirdQuestorAccountingReader(fixture.provider(), 1)
                .readMovements("1", LocalDate.of(2026,7,1), LocalDate.of(2026,7,31)))
                .isInstanceOf(AccountingReadLimitExceededException.class);
        verify(fixture.result()).close(); verify(fixture.statement()).close(); verify(fixture.connection()).close();
    }
    private Fixture fixture(boolean overflow) throws Exception {
        var provider = mock(QuestorConnectionProvider.class); var connection = mock(Connection.class);
        var statement = mock(PreparedStatement.class); var result = mock(ResultSet.class);
        when(provider.openConnection()).thenReturn(connection); when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(result); when(result.next()).thenReturn(true, overflow, false);
        when(result.getString("SOURCE_ENTRY_KEY")).thenReturn("synthetic");
        when(result.getDate("ACCOUNTING_DATE")).thenReturn(Date.valueOf("2026-07-01"));
        when(result.getString("EXTERNAL_ACCOUNT_ID")).thenReturn("synthetic-account");
        when(result.getString("ACCOUNT_KEY")).thenReturn("synthetic-key"); when(result.getString("ACCOUNT_DESCRIPTION")).thenReturn("Synthetic");
        when(result.getString("SIDE")).thenReturn("DEBIT"); when(result.getBigDecimal("AMOUNT")).thenReturn(new BigDecimal("1.2500"));
        when(result.getString("HISTORY")).thenReturn("Synthetic");
        return new Fixture(provider, connection, statement, result);
    }
    private record Fixture(QuestorConnectionProvider provider, Connection connection, PreparedStatement statement, ResultSet result) { }
}

