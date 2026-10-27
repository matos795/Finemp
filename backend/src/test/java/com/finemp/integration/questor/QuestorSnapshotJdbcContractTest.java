package com.finemp.integration.questor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.finemp.accounting.reading.*;
import com.finemp.accounting.source.AccountingSourceType;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Synthetic JDBC contract on PostgreSQL; this is not real Questor/Firebird operational homologation. */
class QuestorSnapshotJdbcContractTest {
    private final QuestorIdentityMapping mapping = new QuestorIdentityMapping("SYN_COMPANIES", "COMPANY_CODE", "TAX_ID");
    @Test void shouldReadProofAndMovementsOnOneActualReadOnlyRepeatableReadConnection() throws Exception {
        try (var postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17"))) {
            postgres.start();
            try (var connection = connect(postgres); var sql = connection.createStatement()) {
                companySchema(sql);
                sql.execute("CREATE TABLE PLANOESPEC(CODIGOEMPRESA INTEGER, CONTACTB INTEGER, CLASSIFCONTA VARCHAR(40), DESCRCONTA VARCHAR(80))");
                sql.execute("CREATE TABLE LCTOCTB(CODIGOEMPRESA INTEGER, CHAVELCTOCTB VARCHAR(40), DATALCTOCTB DATE, CONTACTBCRED INTEGER, CONTACTBDEB INTEGER, VALORLCTOCTB NUMERIC(19,4), COMPLHIST VARCHAR(80))");
                sql.executeUpdate("INSERT INTO PLANOESPEC VALUES (57, 100, '1', 'Synthetic debit'), (57, 200, '2', 'Synthetic credit')");
                sql.executeUpdate("INSERT INTO LCTOCTB VALUES (57, 'synthetic', DATE '2026-07-05', 200, 100, 10.0000, 'Synthetic history')");
            }
            var provider = mock(QuestorConnectionProvider.class);
            var physical = connect(postgres);
            var connection = mock(Connection.class, org.mockito.AdditionalAnswers.delegatesTo(physical)); var checked = new AtomicInteger();
            doAnswer(invocation -> {
                String sql = invocation.getArgument(0);
                if (sql.equals(mapping.sql()) || sql.contains("FROM LCTOCTB L")) {
                    try (var statement = connection.createStatement();
                         var result = statement.executeQuery("SELECT current_setting('transaction_read_only'), current_setting('transaction_isolation')")) {
                        assertThat(result.next()).isTrue(); assertThat(result.getString(1)).isEqualTo("on");
                        assertThat(result.getString(2)).isEqualTo("repeatable read"); checked.incrementAndGet();
                    }
                }
                return physical.prepareStatement(sql);
            }).when(connection).prepareStatement(anyString());
            when(provider.openConnection()).thenReturn(connection);
            var snapshot = reader(provider).readSnapshot(request("49494949000149"));
            assertThat(snapshot.identity().cnpj()).isEqualTo("49494949000149"); assertThat(snapshot.movements()).hasSize(2);
            assertThat(snapshot.movements()).allSatisfy(row -> assertThat(row.amount()).isEqualByComparingTo("10.0000"));
            assertThat(checked).hasValue(2); verify(provider).openConnection();
            verify(connection).rollback(); verify(connection).close(); verify(connection, never()).commit();
        }
    }
    @Test void shouldNeverEchoExpectedIdentityOrReadMovementsForMismatchAmbiguityOrMissingMetadata() throws Exception {
        try (var postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17"))) {
            postgres.start();
            try (var connection = connect(postgres); var sql = connection.createStatement()) { companySchema(sql); }
            var provider = mock(QuestorConnectionProvider.class);
            when(provider.openConnection()).thenAnswer(call -> connect(postgres));
            var actual = new FirebirdQuestorCompanyIdentityReader(provider, mapping, 30).readIdentity(request("50505050000150"));
            assertThat(actual.cnpj()).isEqualTo("49494949000149").isNotEqualTo("50505050000150");
            // There are no movement tables: reading them would fail with a different exception.
            assertThatThrownBy(() -> reader(provider).readSnapshot(request("50505050000150"))).isInstanceOf(AccountingIdentityMismatchException.class);
            try (var connection = connect(postgres); var sql = connection.createStatement()) {
                sql.executeUpdate("INSERT INTO \"SYN_COMPANIES\" VALUES (57, '50.505.050/0001-50')");
            }
            assertThatThrownBy(() -> reader(provider).readSnapshot(request("49494949000149"))).isInstanceOf(AccountingIdentityMismatchException.class);
            var missing = new FirebirdQuestorCompanyIdentityReader(provider, new QuestorIdentityMapping("SYN_COMPANIES", "COMPANY_CODE", "MISSING_FIELD"), 30);
            assertThatThrownBy(() -> missing.readIdentity(request("49494949000149"))).isInstanceOf(AccountingIdentityConfigurationException.class);
        }
    }
    private Connection connect(PostgreSQLContainer postgres) throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }
    private void companySchema(Statement sql) throws SQLException {
        sql.execute("CREATE TABLE \"SYN_COMPANIES\"(\"COMPANY_CODE\" INTEGER, \"TAX_ID\" VARCHAR(18))");
        sql.executeUpdate("INSERT INTO \"SYN_COMPANIES\" VALUES (57, '49.494.949/0001-49')");
    }
    private VerifiedQuestorSnapshotReader reader(QuestorConnectionProvider provider) {
        return new VerifiedQuestorSnapshotReader(provider, new FirebirdQuestorCompanyIdentityReader(provider, mapping, 30),
                new FirebirdQuestorAccountingReader(provider, 10, 30));
    }
    private AccountingReadRequest request(String expected) {
        return new AccountingReadRequest(UUID.randomUUID(), UUID.randomUUID(), AccountingSourceType.QUESTOR, "57", expected,
                LocalDate.of(2026,7,1), LocalDate.of(2026,7,31));
    }
}
