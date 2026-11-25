package com.finemp.integration.questor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDate;

import org.junit.jupiter.api.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

class QuestorSourceInvariantJdbcTest {
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17"));

    @BeforeAll
    static void startDatabase() {
        POSTGRES.start();
    }

    @AfterAll
    static void stopDatabase() {
        POSTGRES.stop();
    }

    @BeforeEach
    void resetSchema() throws Exception {
        try (var connection = connect(); var sql = connection.createStatement()) {
            sql.execute("DROP TABLE IF EXISTS LCTOCTB");
            sql.execute("DROP TABLE IF EXISTS PLANOESPEC");
            sql.execute("""
                    CREATE TABLE PLANOESPEC(
                        CODIGOEMPRESA INTEGER NOT NULL,
                        CONTACTB INTEGER NOT NULL,
                        CLASSIFCONTA VARCHAR(40) NOT NULL,
                        DESCRCONTA VARCHAR(80) NOT NULL,
                        PRIMARY KEY (CODIGOEMPRESA, CONTACTB)
                    )
                    """);
            sql.execute("""
                    CREATE TABLE LCTOCTB(
                        CODIGOEMPRESA INTEGER NOT NULL,
                        CHAVELCTOCTB VARCHAR(40) NOT NULL,
                        DATALCTOCTB DATE NOT NULL,
                        CONTACTBCRED INTEGER,
                        CONTACTBDEB INTEGER,
                        VALORLCTOCTB NUMERIC(19,4) NOT NULL,
                        COMPLHIST VARCHAR(80)
                    )
                    """);
            sql.executeUpdate("""
                    INSERT INTO PLANOESPEC VALUES
                    (1, 100, '1.1', 'Synthetic debit'),
                    (1, 200, '2.1', 'Synthetic credit')
                    """);
        }
    }

    @Test
    void shouldFailClosedForNegativeQuestorAmount() throws Exception {
        insert("negative", 200, 100, new BigDecimal("-1.0000"));

        assertUnexpectedSourceData();
    }

    @Test
    void shouldFailClosedWhenAQuestorEntryHasNoSide() throws Exception {
        insert("no-side", null, null, new BigDecimal("1.0000"));

        assertUnexpectedSourceData();
    }

    @Test
    void shouldFailClosedWhenDebitAndCreditAreTheSameAccount() throws Exception {
        insert("same-side", 100, 100, new BigDecimal("1.0000"));

        assertUnexpectedSourceData();
    }

    @Test
    void shouldFailClosedWhenAReferencedAccountIsMissingFromThePlan() throws Exception {
        insert("missing-plan", 200, 999, new BigDecimal("1.0000"));

        assertUnexpectedSourceData();
    }

    @Test
    void shouldKeepZeroAndPositiveAmountsAndExpandValidEntrySides() throws Exception {
        insert("zero", 200, null, BigDecimal.ZERO);
        insert("positive", 200, 100, new BigDecimal("12.3400"));

        var movements = reader().readMovements(
                "1",
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31));

        assertThat(movements).hasSize(3);
        assertThat(movements)
                .filteredOn(row -> row.sourceEntryKey().equals("zero"))
                .singleElement()
                .satisfies(row -> assertThat(row.amount()).isEqualByComparingTo("0"));
        assertThat(movements)
                .filteredOn(row -> row.sourceEntryKey().equals("positive"))
                .hasSize(2)
                .allSatisfy(row -> assertThat(row.amount()).isEqualByComparingTo("12.3400"));
    }

    private void assertUnexpectedSourceData() {
        assertThatThrownBy(() -> reader().readMovements(
                "1",
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31)))
                .isInstanceOf(QuestorUnexpectedSourceDataException.class)
                .hasMessage("Unexpected accounting source data.");
    }

    private FirebirdQuestorAccountingReader reader() {
        var provider = mock(QuestorConnectionProvider.class);
        try {
            when(provider.openConnection()).thenAnswer(call -> connect());
        } catch (Exception exception) {
            throw new IllegalStateException("Synthetic test setup failed.", exception);
        }
        return new FirebirdQuestorAccountingReader(provider, 20, 30);
    }

    private void insert(
            String key,
            Integer creditAccount,
            Integer debitAccount,
            BigDecimal amount) throws Exception {
        try (var connection = connect();
             var statement = connection.prepareStatement("""
                     INSERT INTO LCTOCTB(
                         CODIGOEMPRESA, CHAVELCTOCTB, DATALCTOCTB,
                         CONTACTBCRED, CONTACTBDEB, VALORLCTOCTB, COMPLHIST)
                     VALUES (1, ?, DATE '2026-07-05', ?, ?, ?, 'Synthetic')
                     """)) {
            statement.setString(1, key);
            if (creditAccount == null) statement.setNull(2, java.sql.Types.INTEGER);
            else statement.setInt(2, creditAccount);
            if (debitAccount == null) statement.setNull(3, java.sql.Types.INTEGER);
            else statement.setInt(3, debitAccount);
            statement.setBigDecimal(4, amount);
            statement.executeUpdate();
        }
    }

    private static Connection connect() throws Exception {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
    }
}
