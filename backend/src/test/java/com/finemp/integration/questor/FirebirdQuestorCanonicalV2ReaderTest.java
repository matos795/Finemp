package com.finemp.integration.questor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.finemp.accounting.canonical.v2.AccountingDataset;
import com.finemp.accounting.canonical.v2.AmountSignPolicy;
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
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

class FirebirdQuestorCanonicalV2ReaderTest {
    private final QuestorIdentityMapping mapping =
            new QuestorIdentityMapping("SYN_COMPANIES", "COMPANY_CODE", "TAX_ID");
    private final Clock clock = Clock.fixed(
            Instant.parse("2026-08-01T12:00:00Z"),
            ZoneOffset.UTC);

    @Test
    void shouldReadCompleteChartAndSingleSourceEntriesInOneReadOnlySnapshot() throws Exception {
        try (var postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17"))) {
            postgres.start();
            try (var connection = connect(postgres); var sql = connection.createStatement()) {
                schema(sql);
                sql.executeUpdate("""
                        INSERT INTO PLANOESPEC VALUES
                        (57,100,'1.1','Debit account','2','1','1','1','2',1,NULL),
                        (57,200,'2.1','Credit account','2','1','-1','2','2',1,'10'),
                        (57,300,'3.1','Unmoved account','1','1','1','3','1',1,NULL)
                        """);
                sql.executeUpdate("""
                        INSERT INTO LCTOCTB VALUES (
                            57,'entry-1',1,NULL,DATE '2026-07-05','LN','DOC-1','FI',
                            100,200,7,'Synthetic history',12.3400,
                            'origin-1','tx-1','1',NULL)
                        """);
            }

            var provider = mock(QuestorConnectionProvider.class);
            var physical = connect(postgres);
            var connection = mock(
                    Connection.class,
                    org.mockito.AdditionalAnswers.delegatesTo(physical));
            var checked = new AtomicInteger();

            doAnswer(invocation -> {
                String sql = invocation.getArgument(0);
                if (sql.equals(mapping.sql())
                        || sql.contains("FROM PLANOESPEC")
                        || sql.contains("FROM LCTOCTB")) {
                    try (var state = connection.createStatement();
                         var result = state.executeQuery(
                                 "SELECT current_setting('transaction_read_only'), current_setting('transaction_isolation')")) {
                        assertThat(result.next()).isTrue();
                        assertThat(result.getString(1)).isEqualTo("on");
                        assertThat(result.getString(2)).isEqualTo("repeatable read");
                        checked.incrementAndGet();
                    }
                }
                return physical.prepareStatement(sql);
            }).when(connection).prepareStatement(anyString());
            when(provider.openConnection()).thenReturn(connection);

            var batch = reader(provider, 10, 10).read(request());

            assertThat(batch.contractVersion()).isEqualTo(2);
            assertThat(batch.observedIdentity().externalReference()).isEqualTo("57");
            assertThat(batch.capabilities().datasets())
                    .isEqualTo(Set.of(AccountingDataset.CHART, AccountingDataset.MOVEMENTS));
            assertThat(batch.capabilities().amountSignPolicy())
                    .isEqualTo(AmountSignPolicy.NON_NEGATIVE_WITH_SIDE);
            assertThat(batch.capabilities().entryKeyScopedBySource()).isTrue();

            assertThat(batch.chart().asOf()).isEqualTo(clock.instant());
            assertThat(batch.chart().accounts()).hasSize(3);
            assertThat(batch.chart().accounts())
                    .extracting(account -> account.sourceAccountId())
                    .containsExactly("100", "200", "300");

            var unmoved = batch.chart().accounts().get(2);
            assertThat(unmoved.description()).isEqualTo("Unmoved account");
            assertThat(unmoved.codes().accountType()).isEqualTo("1");

            assertThat(batch.entries().entries()).singleElement().satisfies(entry -> {
                assertThat(entry.sourceEntryKey()).isEqualTo("entry-1");
                assertThat(entry.accountingDate()).isEqualTo(LocalDate.of(2026, 7, 5));
                assertThat(entry.debitAccountId()).isEqualTo("100");
                assertThat(entry.creditAccountId()).isEqualTo("200");
                assertThat(entry.amount()).isEqualByComparingTo("12.3400");
                assertThat(entry.establishmentCode()).isEqualTo("1");
                assertThat(entry.historyText()).isEqualTo("Synthetic history");
            });

            assertThat(batch.entries().metadata()).singleElement().satisfies(metadata -> {
                assertThat(metadata.sourceEntryKey()).isEqualTo("entry-1");
                assertThat(metadata.sourceRecordedAt()).isNull();
                assertThat(metadata.historyCode()).isEqualTo("7");
                assertThat(metadata.originCode()).isEqualTo("FI");
                assertThat(metadata.entryTypeCode()).isEqualTo("LN");
                assertThat(metadata.dataOriginCode()).isEqualTo("1");
                assertThat(metadata.batchCode()).isNull();
                assertThat(metadata.documentNumber()).isEqualTo("DOC-1");
                assertThat(metadata.operationGroupKey()).isEqualTo("origin-1");
                assertThat(metadata.transactionKey()).isEqualTo("tx-1");
                assertThat(metadata.originEstablishmentCode()).isNull();
            });

            assertThat(checked).hasValue(3);
            verify(provider).openConnection();
            verify(connection).rollback();
            verify(connection).close();
        }
    }

    @Test
    void shouldFailClosedWhenEntryReferencesAnAccountOutsideTheCompleteChart() throws Exception {
        try (var postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17"))) {
            postgres.start();
            try (var connection = connect(postgres); var sql = connection.createStatement()) {
                schema(sql);
                sql.executeUpdate("""
                        INSERT INTO PLANOESPEC VALUES
                        (57,200,'2.1','Credit account','2','1','1','1','2',1,NULL)
                        """);
                sql.executeUpdate("""
                        INSERT INTO LCTOCTB VALUES (
                            57,'missing-account',1,NULL,DATE '2026-07-05','LN',NULL,'FI',
                            999,200,7,NULL,10.0000,NULL,NULL,'1',NULL)
                        """);
            }
            var provider = provider(postgres);

            assertThatThrownBy(() -> reader(provider, 10, 10).read(request()))
                    .isInstanceOf(QuestorUnexpectedSourceDataException.class)
                    .hasMessage("Unexpected accounting source data.");
        }
    }

    @Test
    void shouldEnforceIndependentChartAndEntryBudgets() throws Exception {
        try (var postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17"))) {
            postgres.start();
            try (var connection = connect(postgres); var sql = connection.createStatement()) {
                schema(sql);
                sql.executeUpdate("""
                        INSERT INTO PLANOESPEC VALUES
                        (57,100,'1.1','A','2','1','1','1','2',1,NULL),
                        (57,200,'2.1','B','2','1','1','1','2',1,NULL)
                        """);
            }
            var provider = provider(postgres);

            assertThatThrownBy(() -> reader(provider, 1, 10).read(request()))
                    .isInstanceOf(AccountingReadLimitExceededException.class);
        }
    }

    private FirebirdQuestorCanonicalV2Reader reader(
            QuestorConnectionProvider provider,
            int maxChart,
            int maxEntries) {
        return new FirebirdQuestorCanonicalV2Reader(
                provider,
                new FirebirdQuestorCompanyIdentityReader(provider, mapping, 30),
                clock,
                maxChart,
                maxEntries,
                30);
    }

    private QuestorConnectionProvider provider(PostgreSQLContainer postgres) {
        var provider = mock(QuestorConnectionProvider.class);
        try {
            when(provider.openConnection()).thenAnswer(call -> connect(postgres));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
        return provider;
    }

    private AccountingReadRequest request() {
        return new AccountingReadRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                AccountingSourceType.QUESTOR,
                "57",
                "49494949000149",
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31));
    }

    private Connection connect(PostgreSQLContainer postgres) throws SQLException {
        return DriverManager.getConnection(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword());
    }

    private void schema(Statement sql) throws SQLException {
        sql.execute("""
                CREATE TABLE "SYN_COMPANIES"(
                    "COMPANY_CODE" INTEGER,
                    "TAX_ID" VARCHAR(18))
                """);
        sql.executeUpdate("""
                INSERT INTO "SYN_COMPANIES"
                VALUES (57, '49.494.949/0001-49')
                """);

        sql.execute("""
                CREATE TABLE PLANOESPEC(
                    CODIGOEMPRESA INTEGER NOT NULL,
                    CONTACTB INTEGER NOT NULL,
                    CLASSIFCONTA VARCHAR(40) NOT NULL,
                    DESCRCONTA VARCHAR(80) NOT NULL,
                    TIPOCONTA VARCHAR(10) NOT NULL,
                    TIPOCONTACALC VARCHAR(10) NOT NULL,
                    NATURSALDO VARCHAR(10) NOT NULL,
                    CTRLSALDO VARCHAR(10) NOT NULL,
                    EMITIRBALANCO VARCHAR(10) NOT NULL,
                    CODIGODRE VARCHAR(20),
                    CONTADRE VARCHAR(20),
                    PRIMARY KEY(CODIGOEMPRESA, CONTACTB))
                """);

        sql.execute("""
                CREATE TABLE LCTOCTB(
                    CODIGOEMPRESA INTEGER NOT NULL,
                    CHAVELCTOCTB VARCHAR(40) NOT NULL,
                    CODIGOESTAB INTEGER NOT NULL,
                    CODIGOLOTECTB INTEGER,
                    DATALCTOCTB DATE NOT NULL,
                    TIPOLANCAMENTO VARCHAR(10),
                    NUMERODCTO VARCHAR(40),
                    CODIGOORIGLCTOCTB VARCHAR(10),
                    CONTACTBDEB INTEGER,
                    CONTACTBCRED INTEGER,
                    CODIGOHISTCTB INTEGER,
                    COMPLHIST VARCHAR(300),
                    VALORLCTOCTB NUMERIC(19,4) NOT NULL,
                    CHAVEORIGEM VARCHAR(100),
                    TRANSCTB VARCHAR(100),
                    ORIGEMDADO VARCHAR(10),
                    CODIGOESTABORI INTEGER,
                    PRIMARY KEY(CODIGOEMPRESA, CHAVELCTOCTB))
                """);
    }
}
