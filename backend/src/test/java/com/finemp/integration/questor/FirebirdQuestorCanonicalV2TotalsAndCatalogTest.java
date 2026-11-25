package com.finemp.integration.questor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.finemp.accounting.canonical.v2.AccountingDataset;
import com.finemp.accounting.canonical.v2.CanonicalAccountDailyDebitCreditTotals;
import com.finemp.accounting.canonical.v2.CanonicalAccountMonthlyDebitCreditTotals;
import com.finemp.accounting.canonical.v2.CanonicalOriginCatalogEntry;
import com.finemp.accounting.reading.AccountingReadLimitExceededException;
import com.finemp.accounting.reading.AccountingReadRequest;
import com.finemp.accounting.source.AccountingSourceType;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * C2.5 (#471): totais de débito/crédito por dia (SALDOCTB) e por mês
 * (SALDOCTBMENSAL) e catálogo técnico de origem (ORIGEMLCTOCTB).
 * São totais D/C do período, nunca saldo. JDBC sintético em PostgreSQL;
 * não é homologação Firebird real.
 */
class FirebirdQuestorCanonicalV2TotalsAndCatalogTest {
    private final QuestorIdentityMapping mapping =
            new QuestorIdentityMapping("SYN_COMPANIES", "COMPANY_CODE", "TAX_ID");
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-01T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void shouldMapDailyAndMonthlyDebitCreditTotalsExactlyAndDeclareThem() throws Exception {
        try (var postgres = start()) {
            seed(postgres, """
                    INSERT INTO SALDOCTB VALUES
                    (57,1,100,DATE '2026-07-05','LN',12.3400,0.0000),
                    (57,1,200,DATE '2026-07-05','LN',0.0000,12.3400),
                    (57,1,100,DATE '2026-07-31','LN',99999999999.9999,0.0100)
                    """, """
                    INSERT INTO SALDOCTBMENSAL VALUES
                    (57,1,100,DATE '2026-07-01','LN',100000000012.3399,0.0100),
                    (57,1,200,DATE '2026-07-01','LN',0.0000,12.3400)
                    """, null);

            var batch = reader(provider(postgres)).read(request(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)));

            assertThat(batch.capabilities().datasets()).isEqualTo(Set.of(
                    AccountingDataset.CHART,
                    AccountingDataset.MOVEMENTS,
                    AccountingDataset.DAILY_TOTALS,
                    AccountingDataset.MONTHLY_TOTALS));
            assertThat(batch.dailyTotals()).containsExactly(
                    daily("1", "100", "2026-07-05", "12.3400", "0.0000"),
                    daily("1", "200", "2026-07-05", "0.0000", "12.3400"),
                    daily("1", "100", "2026-07-31", "99999999999.9999", "0.0100"));
            assertThat(batch.monthlyTotals()).containsExactly(
                    monthly("1", "100", 2026, 7, "100000000012.3399", "0.0100"),
                    monthly("1", "200", 2026, 7, "0.0000", "12.3400"));
            assertThat(batch.dailyTotals().get(2).debitTotal().toPlainString()).isEqualTo("99999999999.9999");
            assertThat(batch.originCatalog()).isNull();
        }
    }

    @Test
    void shouldRespectPeriodCompanyAndEstablishmentWithoutMergingEstablishments() throws Exception {
        try (var postgres = start()) {
            seed(postgres, """
                    INSERT INTO SALDOCTB VALUES
                    (57,1,100,DATE '2026-06-30','LN',1.0000,0.0000),
                    (57,1,100,DATE '2026-07-10','LN',2.0000,0.0000),
                    (57,2,100,DATE '2026-07-10','LN',3.0000,0.0000),
                    (57,1,100,DATE '2026-09-01','LN',4.0000,0.0000),
                    (58,1,100,DATE '2026-07-10','LN',5.0000,0.0000)
                    """, """
                    INSERT INTO SALDOCTBMENSAL VALUES
                    (57,1,100,DATE '2026-06-01','LN',10.0000,0.0000),
                    (57,1,100,DATE '2026-07-01','LN',20.0000,0.0000),
                    (57,2,100,DATE '2026-07-01','LN',30.0000,0.0000),
                    (57,1,100,DATE '2026-08-01','LN',40.0000,0.0000),
                    (58,1,100,DATE '2026-07-01','LN',50.0000,0.0000)
                    """, null);

            // Período com julho completo e agosto parcial: o mensal de agosto
            // (mês inteiro) não pertence ao período e não é lido.
            var batch = reader(provider(postgres)).read(request(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 15)));

            assertThat(batch.dailyTotals()).containsExactly(
                    daily("1", "100", "2026-07-10", "2.0000", "0.0000"),
                    daily("2", "100", "2026-07-10", "3.0000", "0.0000"));
            assertThat(batch.monthlyTotals()).containsExactly(
                    monthly("1", "100", 2026, 7, "20.0000", "0.0000"),
                    monthly("2", "100", 2026, 7, "30.0000", "0.0000"));
        }
    }

    @Test
    void shouldNotDeclareMonthlyTotalsWhenThePeriodHasNoCompleteMonth() throws Exception {
        try (var postgres = start()) {
            seed(postgres, """
                    INSERT INTO SALDOCTB VALUES (57,1,100,DATE '2026-07-10','LN',2.0000,0.0000)
                    """, """
                    INSERT INTO SALDOCTBMENSAL VALUES (57,1,100,DATE '2026-07-01','LN',20.0000,0.0000)
                    """, null);

            var batch = reader(provider(postgres)).read(request(LocalDate.of(2026, 7, 5), LocalDate.of(2026, 7, 20)));

            assertThat(batch.capabilities().datasets()).contains(AccountingDataset.DAILY_TOTALS)
                    .doesNotContain(AccountingDataset.MONTHLY_TOTALS);
            assertThat(batch.monthlyTotals()).isNull();
            assertThat(batch.dailyTotals()).singleElement()
                    .isEqualTo(daily("1", "100", "2026-07-10", "2.0000", "0.0000"));
        }
    }

    @Test
    void shouldReadTheOriginCatalogAsRawTechnicalCodesWhenAvailable() throws Exception {
        try (var postgres = start()) {
            seed(postgres, null, null, """
                    CREATE TABLE ORIGEMLCTOCTB(
                        CODIGOORIGLCTOCTB VARCHAR(2) NOT NULL PRIMARY KEY,
                        SYN_CATALOG_TEXT VARCHAR(40) NOT NULL);
                    INSERT INTO ORIGEMLCTOCTB VALUES ('FI','Origem sintética A  '),('FP','Origem sintética B');
                    """);

            var batch = reader(provider(postgres)).read(request(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)));

            assertThat(batch.capabilities().datasets()).contains(AccountingDataset.ORIGIN_CATALOG);
            assertThat(batch.originCatalog()).containsExactly(
                    new CanonicalOriginCatalogEntry("FI", "Origem sintética A"),
                    new CanonicalOriginCatalogEntry("FP", "Origem sintética B"));
        }
    }

    @Test
    void shouldOmitOnlyTheCatalogWhenSelectIsDeniedAndKeepEveryOtherDataset() throws Exception {
        try (var postgres = start()) {
            seed(postgres, """
                    INSERT INTO SALDOCTB VALUES (57,1,100,DATE '2026-07-05','LN',12.3400,0.0000)
                    """, """
                    INSERT INTO SALDOCTBMENSAL VALUES (57,1,100,DATE '2026-07-01','LN',12.3400,0.0000)
                    """, """
                    CREATE TABLE ORIGEMLCTOCTB(
                        CODIGOORIGLCTOCTB VARCHAR(2) NOT NULL PRIMARY KEY,
                        SYN_CATALOG_TEXT VARCHAR(40) NOT NULL);
                    INSERT INTO ORIGEMLCTOCTB VALUES ('FI','Origem sintética A');
                    CREATE ROLE synthetic_reader LOGIN PASSWORD 'synthetic-reader';
                    GRANT SELECT ON "SYN_COMPANIES", PLANOESPEC, LCTOCTB, SALDOCTB, SALDOCTBMENSAL TO synthetic_reader;
                    """);
            var provider = mock(QuestorConnectionProvider.class);
            when(provider.openConnection()).thenAnswer(call ->
                    DriverManager.getConnection(postgres.getJdbcUrl(), "synthetic_reader", "synthetic-reader"));

            var batch = reader(provider).read(request(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)));

            assertThat(batch.capabilities().datasets()).isEqualTo(Set.of(
                    AccountingDataset.CHART,
                    AccountingDataset.MOVEMENTS,
                    AccountingDataset.DAILY_TOTALS,
                    AccountingDataset.MONTHLY_TOTALS));
            assertThat(batch.originCatalog()).isNull();
            assertThat(batch.chart().accounts()).hasSize(2);
            assertThat(batch.entries().entries()).hasSize(1);
            assertThat(batch.dailyTotals()).hasSize(1);
            assertThat(batch.monthlyTotals()).hasSize(1);
        }
    }

    @Test
    void shouldFailClosedWhenTheCatalogShapeDriftsFromTheConfirmedContract() throws Exception {
        try (var postgres = start()) {
            seed(postgres, null, null, """
                    CREATE TABLE ORIGEMLCTOCTB(
                        CODIGOORIGLCTOCTB VARCHAR(2) NOT NULL PRIMARY KEY,
                        SYN_A VARCHAR(40), SYN_B VARCHAR(40));
                    INSERT INTO ORIGEMLCTOCTB VALUES ('FI','x','y');
                    """);

            assertThatThrownBy(() -> reader(provider(postgres))
                    .read(request(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31))))
                    .isInstanceOf(QuestorUnexpectedSourceDataException.class)
                    .hasMessage("Unexpected accounting source data.")
                    .hasNoCause();
        }
    }

    @Test
    void shouldFailClosedForUnexpectedTotalsData() throws Exception {
        String[] invalid = {
                "INSERT INTO SALDOCTB VALUES (57,1,100,DATE '2026-07-05','LN',-1.0000,0.0000)",
                "INSERT INTO SALDOCTB VALUES (57,1,100,DATE '2026-07-05','LN',NULL,0.0000)",
                "INSERT INTO SALDOCTB VALUES (57,1,999,DATE '2026-07-05','LN',1.0000,0.0000)",
                "INSERT INTO SALDOCTBMENSAL VALUES (57,1,100,DATE '2026-07-15','LN',1.0000,0.0000)",
                "INSERT INTO SALDOCTBMENSAL VALUES (57,1,100,DATE '2026-07-01',NULL,1.0000,0.0000)"
        };
        try (var postgres = start()) {
            seed(postgres, null, null, null);
            for (String row : invalid) {
                try (var connection = connect(postgres); var sql = connection.createStatement()) {
                    sql.executeUpdate("DELETE FROM SALDOCTB");
                    sql.executeUpdate("DELETE FROM SALDOCTBMENSAL");
                    sql.executeUpdate(row);
                }

                assertThatThrownBy(() -> reader(provider(postgres))
                        .read(request(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31))))
                        .as(row)
                        .isInstanceOf(QuestorUnexpectedSourceDataException.class)
                        .hasMessage("Unexpected accounting source data.");
            }
        }
    }

    @Test
    void shouldEnforceIndependentBudgetsForTotalsAndCatalog() throws Exception {
        try (var postgres = start()) {
            seed(postgres, """
                    INSERT INTO SALDOCTB VALUES
                    (57,1,100,DATE '2026-07-05','LN',1.0000,0.0000),
                    (57,1,100,DATE '2026-07-06','LN',1.0000,0.0000)
                    """, """
                    INSERT INTO SALDOCTBMENSAL VALUES
                    (57,1,100,DATE '2026-07-01','LN',2.0000,0.0000),
                    (57,1,200,DATE '2026-07-01','LN',0.0000,2.0000)
                    """, """
                    CREATE TABLE ORIGEMLCTOCTB(
                        CODIGOORIGLCTOCTB VARCHAR(2) NOT NULL PRIMARY KEY,
                        SYN_CATALOG_TEXT VARCHAR(40) NOT NULL);
                    INSERT INTO ORIGEMLCTOCTB VALUES ('FI','A'),('FP','B');
                    """);
            var range = request(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

            assertThatThrownBy(() -> reader(provider(postgres), 1, 10, 10).read(range))
                    .isInstanceOf(AccountingReadLimitExceededException.class);
            assertThatThrownBy(() -> reader(provider(postgres), 10, 1, 10).read(range))
                    .isInstanceOf(AccountingReadLimitExceededException.class);
            assertThatThrownBy(() -> reader(provider(postgres), 10, 10, 1).read(range))
                    .isInstanceOf(AccountingReadLimitExceededException.class);
            assertThat(reader(provider(postgres), 2, 2, 2).read(range).originCatalog()).hasSize(2);
        }
    }

    @Test
    void shouldIssueOnlyReadOnlySelectsNeverCommitAndCleanUpWithoutLeakingDetails() throws Exception {
        try (var postgres = start()) {
            seed(postgres, """
                    INSERT INTO SALDOCTB VALUES (57,1,100,DATE '2026-07-05','LN',1.0000,0.0000)
                    """, null, """
                    CREATE TABLE ORIGEMLCTOCTB(
                        CODIGOORIGLCTOCTB VARCHAR(2) NOT NULL PRIMARY KEY,
                        SYN_CATALOG_TEXT VARCHAR(40) NOT NULL);
                    INSERT INTO ORIGEMLCTOCTB VALUES ('FI','A');
                    """);
            List<String> statements = Collections.synchronizedList(new ArrayList<>());
            var physical = connect(postgres);
            var connection = mock(Connection.class, org.mockito.AdditionalAnswers.delegatesTo(physical));
            doAnswer(invocation -> {
                String sql = invocation.getArgument(0);
                statements.add(sql);
                try (var state = physical.createStatement();
                     var result = state.executeQuery("SELECT current_setting('transaction_read_only')")) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getString(1)).isEqualTo("on");
                }
                return physical.prepareStatement(sql);
            }).when(connection).prepareStatement(anyString());
            doAnswer(invocation -> {
                physical.close();
                throw new SQLException("synthetic private cleanup detail /secret/path.fdb");
            }).when(connection).close();
            var provider = mock(QuestorConnectionProvider.class);
            when(provider.openConnection()).thenReturn(connection);

            // Cleanup that cannot confirm the rollback fails closed, without private details.
            assertThatThrownBy(() -> reader(provider).read(request(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31))))
                    .isInstanceOf(QuestorReadException.class)
                    .satisfies(error -> {
                        assertThat(error.getCause()).isNull();
                        assertThat(error.getSuppressed()).isEmpty();
                        assertThat(error.toString()).doesNotContain("synthetic private cleanup detail")
                                .doesNotContain("/secret/path.fdb");
                    });

            assertThat(statements).anyMatch(sql -> sql.contains("FROM SALDOCTB"))
                    .anyMatch(sql -> sql.contains("ORIGEMLCTOCTB"));
            assertThat(statements).allSatisfy(sql ->
                    assertThat(sql.stripLeading().toUpperCase()).startsWith("SELECT"));
            verify(connection, never()).commit();
            verify(connection, never()).createStatement();
            verify(connection).rollback();
            verify(connection).close();
        }
    }

    @Test
    void shouldSanitizeUnexpectedFailuresOfTheTotalsQueries() throws Exception {
        try (var postgres = start()) {
            seed(postgres, null, null, null);
            try (var connection = connect(postgres); var sql = connection.createStatement()) {
                sql.execute("DROP TABLE SALDOCTBMENSAL");
            }

            assertThatThrownBy(() -> reader(provider(postgres))
                    .read(request(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31))))
                    .isInstanceOf(QuestorReadException.class)
                    .satisfies(error -> {
                        assertThat(error.getCause()).isNull();
                        assertThat(error.getSuppressed()).isEmpty();
                        assertThat(error.getMessage()).doesNotContainIgnoringCase("saldoctbmensal")
                                .doesNotContainIgnoringCase("relation")
                                .doesNotContainIgnoringCase("jdbc");
                    });
        }
    }

    private static CanonicalAccountDailyDebitCreditTotals daily(
            String establishment, String account, String day, String debit, String credit) {
        return new CanonicalAccountDailyDebitCreditTotals(
                establishment, account, LocalDate.parse(day), "LN",
                new java.math.BigDecimal(debit), new java.math.BigDecimal(credit));
    }

    private static CanonicalAccountMonthlyDebitCreditTotals monthly(
            String establishment, String account, int year, int month, String debit, String credit) {
        return new CanonicalAccountMonthlyDebitCreditTotals(
                establishment, account, YearMonth.of(year, month), "LN",
                new java.math.BigDecimal(debit), new java.math.BigDecimal(credit));
    }

    private FirebirdQuestorCanonicalV2Reader reader(QuestorConnectionProvider provider) {
        return reader(provider, 10, 10, 10);
    }

    private FirebirdQuestorCanonicalV2Reader reader(
            QuestorConnectionProvider provider, int maxDaily, int maxMonthly, int maxCatalog) {
        return new FirebirdQuestorCanonicalV2Reader(
                provider,
                new FirebirdQuestorCompanyIdentityReader(provider, mapping, 30),
                clock,
                10,
                10,
                maxDaily,
                maxMonthly,
                maxCatalog,
                30);
    }

    private QuestorConnectionProvider provider(PostgreSQLContainer postgres) throws Exception {
        var provider = mock(QuestorConnectionProvider.class);
        when(provider.openConnection()).thenAnswer(call -> connect(postgres));
        return provider;
    }

    private static AccountingReadRequest request(LocalDate start, LocalDate end) {
        return new AccountingReadRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                AccountingSourceType.QUESTOR,
                "57",
                "49494949000149",
                start,
                end);
    }

    private static PostgreSQLContainer start() {
        var postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));
        postgres.start();
        return postgres;
    }

    private static Connection connect(PostgreSQLContainer postgres) throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private static void seed(PostgreSQLContainer postgres, String daily, String monthly, String extra)
            throws SQLException {
        try (var connection = connect(postgres); var sql = connection.createStatement()) {
            schema(sql);
            sql.executeUpdate("""
                    INSERT INTO PLANOESPEC VALUES
                    (57,100,'1.1','Synthetic debit account','2','1','1','1','2',NULL,NULL),
                    (57,200,'2.1','Synthetic credit account','2','1','2','1','2',NULL,NULL)
                    """);
            sql.executeUpdate("""
                    INSERT INTO LCTOCTB VALUES (
                        57,'entry-1',1,NULL,DATE '2026-07-05','LN',NULL,'FI',
                        100,200,7,NULL,12.3400,'group-1',NULL,'1',NULL)
                    """);
            if (daily != null) sql.executeUpdate(daily);
            if (monthly != null) sql.executeUpdate(monthly);
            if (extra != null) sql.execute(extra);
        }
    }

    private static void schema(Statement sql) throws SQLException {
        sql.execute("""
                CREATE TABLE "SYN_COMPANIES"("COMPANY_CODE" INTEGER, "TAX_ID" VARCHAR(18));
                INSERT INTO "SYN_COMPANIES" VALUES (57, '49.494.949/0001-49');
                CREATE TABLE PLANOESPEC(
                    CODIGOEMPRESA INTEGER NOT NULL, CONTACTB INTEGER NOT NULL,
                    CLASSIFCONTA VARCHAR(40) NOT NULL, DESCRCONTA VARCHAR(80) NOT NULL,
                    TIPOCONTA VARCHAR(10) NOT NULL, TIPOCONTACALC VARCHAR(10) NOT NULL,
                    NATURSALDO VARCHAR(10) NOT NULL, CTRLSALDO VARCHAR(10) NOT NULL,
                    EMITIRBALANCO VARCHAR(10) NOT NULL, CODIGODRE VARCHAR(20), CONTADRE VARCHAR(20),
                    PRIMARY KEY(CODIGOEMPRESA, CONTACTB));
                CREATE TABLE LCTOCTB(
                    CODIGOEMPRESA INTEGER NOT NULL, CHAVELCTOCTB VARCHAR(40) NOT NULL,
                    CODIGOESTAB INTEGER NOT NULL, CODIGOLOTECTB INTEGER, DATALCTOCTB DATE NOT NULL,
                    TIPOLANCAMENTO VARCHAR(10), NUMERODCTO VARCHAR(40), CODIGOORIGLCTOCTB VARCHAR(10),
                    CONTACTBDEB INTEGER, CONTACTBCRED INTEGER, CODIGOHISTCTB INTEGER, COMPLHIST VARCHAR(300),
                    VALORLCTOCTB NUMERIC(19,4) NOT NULL, CHAVEORIGEM VARCHAR(100), TRANSCTB VARCHAR(100),
                    ORIGEMDADO VARCHAR(10), CODIGOESTABORI INTEGER,
                    PRIMARY KEY(CODIGOEMPRESA, CHAVELCTOCTB));
                CREATE TABLE SALDOCTB(
                    CODIGOEMPRESA INTEGER NOT NULL, CODIGOESTAB INTEGER NOT NULL, CONTACTB INTEGER NOT NULL,
                    DATASALDO DATE NOT NULL, TIPOLANCAMENTO CHAR(2),
                    VALORDEB NUMERIC(19,4), VALORCRED NUMERIC(19,4));
                CREATE TABLE SALDOCTBMENSAL(
                    CODIGOEMPRESA INTEGER NOT NULL, CODIGOESTAB INTEGER NOT NULL, CONTACTB INTEGER NOT NULL,
                    DATASALDO DATE NOT NULL, TIPOLANCAMENTO CHAR(2),
                    VALORDEB NUMERIC(19,4), VALORCRED NUMERIC(19,4));
                """);
    }
}
