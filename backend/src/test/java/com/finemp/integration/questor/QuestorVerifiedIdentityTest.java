package com.finemp.integration.questor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.finemp.accounting.reading.*;
import com.finemp.accounting.source.AccountingSourceType;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class QuestorVerifiedIdentityTest {
    private final AccountingReadRequest request = new AccountingReadRequest(UUID.randomUUID(), UUID.randomUUID(), AccountingSourceType.QUESTOR,
            "57", "49494949000149", LocalDate.of(2026,7,1), LocalDate.of(2026,7,31));
    private final QuestorIdentityMapping mapping = new QuestorIdentityMapping("SYN_COMPANIES", "COMPANY_CODE", "TAX_ID");
    @Test void shouldReadObservedReferenceAndTaxIdInsteadOfEchoingTheExpectedIdentity() throws Exception {
        var f = fixture();
        var observed = reader(f).readIdentity(request);
        assertThat(observed.externalReference()).isEqualTo("58"); assertThat(observed.cnpj()).isEqualTo("50505050000150");
        assertThat(observed.matches(request)).isFalse();
        var sql = ArgumentCaptor.forClass(String.class); verify(f.connection()).prepareStatement(sql.capture());
        assertThat(sql.getValue()).isEqualTo(mapping.sql()).doesNotContain(request.expectedCompanyCnpj());
        verify(f.statement()).setInt(1, 57); verify(f.statement()).setMaxRows(2); verify(f.statement()).setQueryTimeout(30);
        verify(f.connection()).setReadOnly(true); verify(f.connection()).setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
        verify(f.connection()).setAutoCommit(false); verify(f.connection()).rollback(); verify(f.connection()).close(); verify(f.connection(), never()).commit();
    }
    @Test void shouldRejectIdentifierInjectionMissingMappingAndInvalidReferenceBeforeOpeningTheDatabase() throws Exception {
        var f = fixture();
        for (var table : new String[]{"", "TABLE;DROP", "TABLE.NAME", "TABLE\"X", "x".repeat(64)})
            assertThatThrownBy(() -> new QuestorIdentityMapping(table, "CODE", "CNPJ")).isInstanceOf(IllegalArgumentException.class);
        var invalid = new AccountingReadRequest(request.accountingSourceId(), request.companyId(), AccountingSourceType.QUESTOR,
                "not-a-code", request.expectedCompanyCnpj(), request.startDate(), request.endDate());
        assertThatThrownBy(() -> reader(f).readIdentity(invalid)).isInstanceOf(AccountingIdentityConfigurationException.class);
        verifyNoInteractions(f.provider());
    }
    @Test void shouldValidateExactTableColumnAndTypesBeforeExecutingTheIdentityQuery() throws Exception {
        var missing = fixture(); when(missing.tables().next()).thenReturn(false);
        assertThatThrownBy(() -> reader(missing).readIdentity(request)).isInstanceOf(AccountingIdentityConfigurationException.class);
        verify(missing.connection(), never()).prepareStatement(anyString());
        var wrongType = fixture(); when(wrongType.columns().getInt("DATA_TYPE")).thenReturn(Types.INTEGER, Types.INTEGER);
        assertThatThrownBy(() -> reader(wrongType).readIdentity(request)).isInstanceOf(AccountingIdentityConfigurationException.class);
        verify(wrongType.connection(), never()).prepareStatement(anyString());
    }
    @Test void shouldRefuseMissingAndAmbiguousRowsRatherThanPickOneOrUseAnExpectedFallback() throws Exception {
        var missing = fixture(); when(missing.rows().next()).thenReturn(false);
        assertThatThrownBy(() -> reader(missing).readIdentity(request)).isInstanceOf(AccountingIdentityMismatchException.class);
        var ambiguous = fixture(); when(ambiguous.rows().next()).thenReturn(true, true, false);
        assertThatThrownBy(() -> reader(ambiguous).readIdentity(request)).isInstanceOf(AccountingIdentityMismatchException.class);
        verify(ambiguous.rows()).close(); verify(ambiguous.connection()).rollback(); verify(ambiguous.connection()).close();
    }
    @Test void shouldKeepPrivateDriverAndCloseErrorsOutOfTheThrownDiagnostic() throws Exception {
        var f = fixture(); when(f.statement().executeQuery()).thenThrow(new SQLException("synthetic private connection detail"));
        doThrow(new SQLException("synthetic private close detail")).when(f.connection()).close();
        assertThatThrownBy(() -> reader(f).readIdentity(request)).isInstanceOf(QuestorReadException.class).hasNoCause()
                .hasMessage("Questor identity read unavailable.").satisfies(error -> assertThat(error.getSuppressed()).isEmpty());
        verify(f.connection()).rollback(); verify(f.connection()).close();
    }
    @Test void shouldNeverReadMovementsWhenActualIdentityDiffersAndStillReleaseTheSourceSession() throws Exception {
        var f = fixture(); var movements = mock(FirebirdQuestorAccountingReader.class);
        var snapshot = new VerifiedQuestorSnapshotReader(f.provider(), reader(f), movements);
        assertThatThrownBy(() -> snapshot.readSnapshot(request)).isInstanceOf(AccountingIdentityMismatchException.class);
        verifyNoInteractions(movements); verify(f.provider()).openConnection(); verify(f.connection()).rollback(); verify(f.connection()).close();
    }
    @Test void shouldUseTheSameConnectionForProofAndMovementsWithoutOpeningAnotherSourceSession() throws Exception {
        var f = fixture(); var identity = mock(FirebirdQuestorCompanyIdentityReader.class); var movements = mock(FirebirdQuestorAccountingReader.class);
        when(identity.readOn(f.connection(), request)).thenReturn(new ExternalCompanyIdentity("57", "49.494.949/0001-49"));
        when(movements.readOn(f.connection(), "57", request.startDate(), request.endDate())).thenReturn(List.of());
        var result = new VerifiedQuestorSnapshotReader(f.provider(), identity, movements).readSnapshot(request);
        assertThat(result.identity().matches(request)).isTrue(); assertThat(result.movements()).isEmpty();
        var order = inOrder(identity, movements);
        order.verify(identity).readOn(f.connection(), request); order.verify(movements).readOn(f.connection(), "57", request.startDate(), request.endDate());
        verify(f.provider()).openConnection(); verify(f.connection()).rollback(); verify(f.connection()).close();
    }
    private FirebirdQuestorCompanyIdentityReader reader(Fixture fixture) { return new FirebirdQuestorCompanyIdentityReader(fixture.provider(), mapping, 30); }
    private Fixture fixture() throws Exception {
        var provider = mock(QuestorConnectionProvider.class); var connection = mock(Connection.class); var metadata = mock(DatabaseMetaData.class);
        var tables = mock(ResultSet.class); var columns = mock(ResultSet.class); var statement = mock(PreparedStatement.class); var rows = mock(ResultSet.class);
        when(provider.openConnection()).thenReturn(connection); when(connection.getMetaData()).thenReturn(metadata); when(metadata.getSearchStringEscape()).thenReturn("\\");
        when(metadata.getTables(isNull(), isNull(), anyString(), any(String[].class))).thenReturn(tables);
        when(tables.next()).thenReturn(true, false); when(tables.getString("TABLE_NAME")).thenReturn("SYN_COMPANIES");
        when(metadata.getColumns(isNull(), isNull(), anyString(), eq("%"))).thenReturn(columns);
        when(columns.next()).thenReturn(true, true, false); when(columns.getString("TABLE_NAME")).thenReturn("SYN_COMPANIES");
        when(columns.getString("COLUMN_NAME")).thenReturn("COMPANY_CODE", "TAX_ID");
        when(columns.getInt("DATA_TYPE")).thenReturn(Types.INTEGER, Types.VARCHAR); when(columns.getInt("COLUMN_SIZE")).thenReturn(18);
        when(columns.getInt("DECIMAL_DIGITS")).thenReturn(0); when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rows); when(rows.next()).thenReturn(true, false);
        when(rows.getInt("FINEMP_OBS_REF")).thenReturn(58); when(rows.getString("FINEMP_OBS_CNPJ")).thenReturn("50.505.050/0001-50");
        return new Fixture(provider, connection, tables, columns, statement, rows);
    }
    private record Fixture(QuestorConnectionProvider provider, Connection connection, ResultSet tables, ResultSet columns, PreparedStatement statement, ResultSet rows) { }
}
