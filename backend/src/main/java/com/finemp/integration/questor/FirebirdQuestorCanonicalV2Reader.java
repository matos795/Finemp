package com.finemp.integration.questor;

import com.finemp.accounting.canonical.v2.AccountingDataset;
import com.finemp.accounting.canonical.v2.CanonicalAccountDailyDebitCreditTotals;
import com.finemp.accounting.canonical.v2.CanonicalAccountMonthlyDebitCreditTotals;
import com.finemp.accounting.canonical.v2.CanonicalOriginCatalogEntry;
import com.finemp.accounting.canonical.v2.AmountSignPolicy;
import com.finemp.accounting.canonical.v2.CanonicalAccountingAccount;
import com.finemp.accounting.canonical.v2.CanonicalAccountingBatch;
import com.finemp.accounting.canonical.v2.CanonicalAccountingEntry;
import com.finemp.accounting.canonical.v2.CanonicalChartSnapshot;
import com.finemp.accounting.canonical.v2.CanonicalEntriesDataset;
import com.finemp.accounting.canonical.v2.CanonicalEntrySourceMetadata;
import com.finemp.accounting.canonical.v2.CanonicalV2BatchValidator;
import com.finemp.accounting.canonical.v2.SourceAccountCodes;
import com.finemp.accounting.canonical.v2.SourceCapabilities;
import com.finemp.accounting.reading.AccountingIdentityConfigurationException;
import com.finemp.accounting.reading.AccountingIdentityMismatchException;
import com.finemp.accounting.reading.AccountingReadLimitExceededException;
import com.finemp.accounting.reading.AccountingReadRequest;
import com.finemp.accounting.sync.execution.SyncPublicationRejectedException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.sql.ResultSetMetaData;
import java.sql.Types;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class FirebirdQuestorCanonicalV2Reader {
    private static final String CHART_SQL = """
            SELECT
                CONTACTB AS SOURCE_ACCOUNT_ID,
                CLASSIFCONTA AS CLASSIFICATION_CODE,
                DESCRCONTA AS ACCOUNT_DESCRIPTION,
                TIPOCONTA AS ACCOUNT_TYPE,
                TIPOCONTACALC AS CALCULATION_TYPE,
                NATURSALDO AS BALANCE_NATURE,
                CTRLSALDO AS BALANCE_CONTROL,
                EMITIRBALANCO AS SHOW_IN_BALANCE_SHEET,
                CODIGODRE AS NATIVE_DRE_CODE,
                CONTADRE AS NATIVE_DRE_ACCOUNT
            FROM PLANOESPEC
            WHERE CODIGOEMPRESA = ?
            ORDER BY CONTACTB
            """;

    private static final String ENTRIES_SQL = """
            SELECT
                CHAVELCTOCTB AS SOURCE_ENTRY_KEY,
                DATALCTOCTB AS ACCOUNTING_DATE,
                CONTACTBDEB AS DEBIT_ACCOUNT_ID,
                CONTACTBCRED AS CREDIT_ACCOUNT_ID,
                VALORLCTOCTB AS AMOUNT,
                CODIGOESTAB AS ESTABLISHMENT_CODE,
                COMPLHIST AS HISTORY_TEXT,
                CODIGOHISTCTB AS HISTORY_CODE,
                CODIGOORIGLCTOCTB AS ORIGIN_CODE,
                TIPOLANCAMENTO AS ENTRY_TYPE_CODE,
                ORIGEMDADO AS DATA_ORIGIN_CODE,
                CODIGOLOTECTB AS BATCH_CODE,
                NUMERODCTO AS DOCUMENT_NUMBER,
                CHAVEORIGEM AS OPERATION_GROUP_KEY,
                TRANSCTB AS TRANSACTION_KEY,
                CODIGOESTABORI AS ORIGIN_ESTABLISHMENT_CODE
            FROM LCTOCTB
            WHERE CODIGOEMPRESA = ?
              AND DATALCTOCTB BETWEEN ? AND ?
            ORDER BY DATALCTOCTB, CHAVELCTOCTB
            """;

    /*
     * Totais de débito e crédito do período (C1b: batem 100% com LCTOCTB no
     * mesmo grão). Não são saldo: não há coluna de saldo acumulado.
     */
    private static final String DAILY_TOTALS_SQL = """
            SELECT
                CODIGOESTAB AS ESTABLISHMENT_CODE,
                CONTACTB AS SOURCE_ACCOUNT_ID,
                DATASALDO AS TOTALS_DAY,
                TIPOLANCAMENTO AS ENTRY_TYPE_CODE,
                VALORDEB AS DEBIT_TOTAL,
                VALORCRED AS CREDIT_TOTAL
            FROM SALDOCTB
            WHERE CODIGOEMPRESA = ?
              AND DATASALDO BETWEEN ? AND ?
            ORDER BY DATASALDO, CODIGOESTAB, CONTACTB, TIPOLANCAMENTO
            """;

    /* SALDOCTBMENSAL guarda o mês inteiro em DATASALDO = dia 1 (C1b). */
    private static final String MONTHLY_TOTALS_SQL = """
            SELECT
                CODIGOESTAB AS ESTABLISHMENT_CODE,
                CONTACTB AS SOURCE_ACCOUNT_ID,
                DATASALDO AS TOTALS_MONTH,
                TIPOLANCAMENTO AS ENTRY_TYPE_CODE,
                VALORDEB AS DEBIT_TOTAL,
                VALORCRED AS CREDIT_TOTAL
            FROM SALDOCTBMENSAL
            WHERE CODIGOEMPRESA = ?
              AND DATASALDO BETWEEN ? AND ?
            ORDER BY DATASALDO, CODIGOESTAB, CONTACTB, TIPOLANCAMENTO
            """;

    /*
     * C1 confirmou ORIGEMLCTOCTB com exatamente duas colunas: o código
     * (CODIGOORIGLCTOCTB) e uma descrição de catálogo cujo nome não foi
     * registrado. Lê pela forma confirmada, sem inventar o nome da coluna.
     */
    private static final String ORIGIN_CATALOG_SQL = """
            SELECT * FROM ORIGEMLCTOCTB
            """;
    private static final String ORIGIN_CODE_COLUMN = "CODIGOORIGLCTOCTB";
    private static final Set<String> CATALOG_UNAVAILABLE_SQL_STATES = Set.of(
            "28000", // Firebird/Jaybird: sem privilégio
            "42501", // SQL padrão: privilégio insuficiente
            "42S02", // Firebird/Jaybird: tabela desconhecida
            "42P01"); // SQL padrão (PostgreSQL): tabela inexistente
    private static final int FIREBIRD_NO_PRIVILEGE = 335544352;
    private static final int FIREBIRD_UNKNOWN_TABLE = 335544580;

    private final QuestorConnectionProvider provider;
    private final FirebirdQuestorCompanyIdentityReader identities;
    private final Clock clock;
    private final int maxChartRecords;
    private final int maxEntryRecords;
    private final int maxDailyTotalsRecords;
    private final int maxMonthlyTotalsRecords;
    private final int maxOriginCatalogRecords;
    private final int timeoutSeconds;

    public FirebirdQuestorCanonicalV2Reader(
            QuestorConnectionProvider provider,
            FirebirdQuestorCompanyIdentityReader identities,
            Clock clock) {
        this(provider, identities, clock, 25000, 25000, 30);
    }

    public FirebirdQuestorCanonicalV2Reader(
            QuestorConnectionProvider provider,
            FirebirdQuestorCompanyIdentityReader identities,
            Clock clock,
            int maxChartRecords,
            int maxEntryRecords,
            int timeoutSeconds) {
        this(provider, identities, clock, maxChartRecords, maxEntryRecords,
                25000, 25000, 1000, timeoutSeconds);
    }

    public FirebirdQuestorCanonicalV2Reader(
            QuestorConnectionProvider provider,
            FirebirdQuestorCompanyIdentityReader identities,
            Clock clock,
            int maxChartRecords,
            int maxEntryRecords,
            int maxDailyTotalsRecords,
            int maxMonthlyTotalsRecords,
            int maxOriginCatalogRecords,
            int timeoutSeconds) {
        for (int limit : new int[] {
                maxChartRecords, maxEntryRecords, maxDailyTotalsRecords,
                maxMonthlyTotalsRecords, maxOriginCatalogRecords}) {
            if (limit < 1 || limit == Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Invalid Questor v2 reader resource limit.");
            }
        }
        if (timeoutSeconds < 1 || timeoutSeconds > 120) {
            throw new IllegalArgumentException("Invalid Questor v2 query timeout.");
        }
        this.provider = Objects.requireNonNull(provider);
        this.identities = Objects.requireNonNull(identities);
        this.clock = Objects.requireNonNull(clock);
        this.maxChartRecords = maxChartRecords;
        this.maxEntryRecords = maxEntryRecords;
        this.maxDailyTotalsRecords = maxDailyTotalsRecords;
        this.maxMonthlyTotalsRecords = maxMonthlyTotalsRecords;
        this.maxOriginCatalogRecords = maxOriginCatalogRecords;
        this.timeoutSeconds = timeoutSeconds;
    }

    public CanonicalAccountingBatch read(AccountingReadRequest request) {
        int companyCode = FirebirdQuestorCompanyIdentityReader.requireCode(request);
        Instant observedAt = clock.instant();

        try (var session = QuestorReadOnlySession.open(provider)) {
            var identity = identities.readOn(session.connection(), request);
            if (!identity.matches(request)) {
                throw new AccountingIdentityMismatchException();
            }

            var chart = readChartOn(session.connection(), companyCode, observedAt);
            var accountIds = new LinkedHashSet<String>();
            chart.accounts().forEach(account -> accountIds.add(account.sourceAccountId()));

            var entries = readEntriesOn(
                    session.connection(),
                    companyCode,
                    request,
                    accountIds);

            var datasets = EnumSet.of(
                    AccountingDataset.CHART,
                    AccountingDataset.MOVEMENTS,
                    AccountingDataset.DAILY_TOTALS);

            var dailyTotals = readDailyTotalsOn(session.connection(), companyCode, request);

            List<CanonicalAccountMonthlyDebitCreditTotals> monthlyTotals = null;
            var months = completeMonths(request.startDate(), request.endDate());
            if (months != null) {
                monthlyTotals = readMonthlyTotalsOn(session.connection(), companyCode, months);
                datasets.add(AccountingDataset.MONTHLY_TOTALS);
            }

            // Último: catálogo é opcional e sua falta não pode afetar os demais datasets.
            var originCatalog = readOriginCatalogOn(session.connection());
            if (originCatalog != null) {
                datasets.add(AccountingDataset.ORIGIN_CATALOG);
            }

            var batch = new CanonicalAccountingBatch(
                    CanonicalAccountingBatch.VERSION,
                    identity,
                    new SourceCapabilities(
                            datasets,
                            AmountSignPolicy.NON_NEGATIVE_WITH_SIDE,
                            true),
                    chart,
                    entries,
                    dailyTotals,
                    monthlyTotals,
                    originCatalog,
                    null,
                    null);

            try {
                return CanonicalV2BatchValidator.validate(
                        batch,
                        request.startDate(),
                        request.endDate(),
                        largestBudget());
            } catch (SyncPublicationRejectedException unexpectedSourceShape) {
                throw new QuestorUnexpectedSourceDataException();
            }
        } catch (AccountingIdentityMismatchException mismatch) {
            throw new AccountingIdentityMismatchException();
        } catch (AccountingIdentityConfigurationException unsupported) {
            throw new AccountingIdentityConfigurationException();
        } catch (AccountingReadLimitExceededException overflow) {
            throw new AccountingReadLimitExceededException();
        } catch (QuestorUnexpectedSourceDataException unexpected) {
            throw new QuestorUnexpectedSourceDataException();
        } catch (SQLException | RuntimeException unavailable) {
            throw new QuestorReadException("Questor canonical v2 read unavailable.", null);
        }
    }

    CanonicalChartSnapshot readChartOn(
            Connection connection,
            int companyCode,
            Instant observedAt) throws SQLException {
        List<CanonicalAccountingAccount> accounts = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(CHART_SQL)) {
            statement.setInt(1, companyCode);
            statement.setMaxRows(maxChartRecords + 1);
            statement.setQueryTimeout(timeoutSeconds);

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    if (accounts.size() >= maxChartRecords) {
                        throw new AccountingReadLimitExceededException();
                    }

                    accounts.add(new CanonicalAccountingAccount(
                            required(result.getString("SOURCE_ACCOUNT_ID")),
                            required(result.getString("CLASSIFICATION_CODE")),
                            required(result.getString("ACCOUNT_DESCRIPTION")),
                            new SourceAccountCodes(
                                    required(result.getString("ACCOUNT_TYPE")),
                                    required(result.getString("CALCULATION_TYPE")),
                                    required(result.getString("BALANCE_NATURE")),
                                    required(result.getString("BALANCE_CONTROL")),
                                    required(result.getString("SHOW_IN_BALANCE_SHEET")),
                                    optional(result.getString("NATIVE_DRE_CODE")),
                                    optional(result.getString("NATIVE_DRE_ACCOUNT")))));
                }
            }
        }

        return new CanonicalChartSnapshot(observedAt, accounts);
    }

    CanonicalEntriesDataset readEntriesOn(
            Connection connection,
            int companyCode,
            AccountingReadRequest request,
            Set<String> chartAccountIds) throws SQLException {
        List<CanonicalAccountingEntry> entries = new ArrayList<>();
        List<CanonicalEntrySourceMetadata> metadata = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(ENTRIES_SQL)) {
            statement.setInt(1, companyCode);
            statement.setDate(2, Date.valueOf(request.startDate()));
            statement.setDate(3, Date.valueOf(request.endDate()));
            statement.setMaxRows(maxEntryRecords + 1);
            statement.setQueryTimeout(timeoutSeconds);

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    if (entries.size() >= maxEntryRecords) {
                        throw new AccountingReadLimitExceededException();
                    }

                    String sourceEntryKey = required(result.getString("SOURCE_ENTRY_KEY"));
                    String debitAccountId = optional(result.getString("DEBIT_ACCOUNT_ID"));
                    String creditAccountId = optional(result.getString("CREDIT_ACCOUNT_ID"));
                    BigDecimal amount = result.getBigDecimal("AMOUNT");
                    Date accountingDate = result.getDate("ACCOUNTING_DATE");
                    if (accountingDate == null) {
                        throw new QuestorUnexpectedSourceDataException();
                    }

                    requireValidQuestorEntry(
                            debitAccountId,
                            creditAccountId,
                            amount,
                            chartAccountIds);

                    entries.add(new CanonicalAccountingEntry(
                            sourceEntryKey,
                            accountingDate.toLocalDate(),
                            debitAccountId,
                            creditAccountId,
                            amount,
                            required(result.getString("ESTABLISHMENT_CODE")),
                            result.getString("HISTORY_TEXT")));

                    metadata.add(new CanonicalEntrySourceMetadata(
                            sourceEntryKey,
                            null,
                            optional(result.getString("HISTORY_CODE")),
                            optional(result.getString("ORIGIN_CODE")),
                            optional(result.getString("ENTRY_TYPE_CODE")),
                            optional(result.getString("DATA_ORIGIN_CODE")),
                            optional(result.getString("BATCH_CODE")),
                            optional(result.getString("DOCUMENT_NUMBER")),
                            optional(result.getString("OPERATION_GROUP_KEY")),
                            optional(result.getString("TRANSACTION_KEY")),
                            optional(result.getString("ORIGIN_ESTABLISHMENT_CODE"))));
                }
            }
        }

        return new CanonicalEntriesDataset(
                request.startDate(),
                request.endDate(),
                entries,
                metadata);
    }

    List<CanonicalAccountDailyDebitCreditTotals> readDailyTotalsOn(
            Connection connection,
            int companyCode,
            AccountingReadRequest request) throws SQLException {
        List<CanonicalAccountDailyDebitCreditTotals> totals = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(DAILY_TOTALS_SQL)) {
            statement.setInt(1, companyCode);
            statement.setDate(2, Date.valueOf(request.startDate()));
            statement.setDate(3, Date.valueOf(request.endDate()));
            statement.setMaxRows(maxDailyTotalsRecords + 1);
            statement.setQueryTimeout(timeoutSeconds);

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    if (totals.size() >= maxDailyTotalsRecords) {
                        throw new AccountingReadLimitExceededException();
                    }
                    Date day = result.getDate("TOTALS_DAY");
                    if (day == null) {
                        throw new QuestorUnexpectedSourceDataException();
                    }
                    totals.add(new CanonicalAccountDailyDebitCreditTotals(
                            required(result.getString("ESTABLISHMENT_CODE")),
                            required(result.getString("SOURCE_ACCOUNT_ID")),
                            day.toLocalDate(),
                            required(result.getString("ENTRY_TYPE_CODE")),
                            requiredTotal(result.getBigDecimal("DEBIT_TOTAL")),
                            requiredTotal(result.getBigDecimal("CREDIT_TOTAL"))));
                }
            }
        }
        return totals;
    }

    List<CanonicalAccountMonthlyDebitCreditTotals> readMonthlyTotalsOn(
            Connection connection,
            int companyCode,
            CompleteMonths months) throws SQLException {
        List<CanonicalAccountMonthlyDebitCreditTotals> totals = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(MONTHLY_TOTALS_SQL)) {
            statement.setInt(1, companyCode);
            statement.setDate(2, Date.valueOf(months.first().atDay(1)));
            statement.setDate(3, Date.valueOf(months.last().atEndOfMonth()));
            statement.setMaxRows(maxMonthlyTotalsRecords + 1);
            statement.setQueryTimeout(timeoutSeconds);

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    if (totals.size() >= maxMonthlyTotalsRecords) {
                        throw new AccountingReadLimitExceededException();
                    }
                    Date stored = result.getDate("TOTALS_MONTH");
                    if (stored == null || stored.toLocalDate().getDayOfMonth() != 1) {
                        throw new QuestorUnexpectedSourceDataException();
                    }
                    totals.add(new CanonicalAccountMonthlyDebitCreditTotals(
                            required(result.getString("ESTABLISHMENT_CODE")),
                            required(result.getString("SOURCE_ACCOUNT_ID")),
                            YearMonth.from(stored.toLocalDate()),
                            required(result.getString("ENTRY_TYPE_CODE")),
                            requiredTotal(result.getBigDecimal("DEBIT_TOTAL")),
                            requiredTotal(result.getBigDecimal("CREDIT_TOTAL"))));
                }
            }
        }
        return totals;
    }

    /**
     * Catálogo técnico de origem: códigos e descrições crus do produto Questor,
     * sem interpretação de negócio. Retorna {@code null} (capability ausente)
     * quando a origem nega SELECT, não tem a tabela ou não tem a forma
     * confirmada; qualquer outra falha segue o caminho fail-closed do reader.
     */
    List<CanonicalOriginCatalogEntry> readOriginCatalogOn(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(ORIGIN_CATALOG_SQL)) {
            statement.setMaxRows(maxOriginCatalogRecords + 1);
            statement.setQueryTimeout(timeoutSeconds);

            try (ResultSet result = statement.executeQuery()) {
                int[] columns = catalogColumns(result.getMetaData());
                List<CanonicalOriginCatalogEntry> catalog = new ArrayList<>();
                while (result.next()) {
                    if (catalog.size() >= maxOriginCatalogRecords) {
                        throw new AccountingReadLimitExceededException();
                    }
                    catalog.add(new CanonicalOriginCatalogEntry(
                            required(result.getString(columns[0])),
                            required(result.getString(columns[1]))));
                }
                return catalog;
            }
        } catch (SQLException failure) {
            if (catalogUnavailable(failure)) {
                return null;
            }
            throw failure;
        }
    }

    private static int[] catalogColumns(ResultSetMetaData metadata) throws SQLException {
        if (metadata.getColumnCount() != 2) {
            throw new QuestorUnexpectedSourceDataException();
        }
        int code = 0;
        int description = 0;
        for (int column = 1; column <= 2; column++) {
            if (ORIGIN_CODE_COLUMN.equalsIgnoreCase(metadata.getColumnName(column))) {
                code = column;
            } else if (characterColumn(metadata.getColumnType(column))) {
                description = column;
            }
        }
        if (code == 0 || description == 0) {
            throw new QuestorUnexpectedSourceDataException();
        }
        return new int[] {code, description};
    }

    private static boolean characterColumn(int type) {
        return type == Types.VARCHAR || type == Types.CHAR || type == Types.NVARCHAR
                || type == Types.NCHAR || type == Types.LONGVARCHAR || type == Types.LONGNVARCHAR;
    }

    private static boolean catalogUnavailable(SQLException failure) {
        for (SQLException current = failure; current != null; current = current.getNextException()) {
            if (CATALOG_UNAVAILABLE_SQL_STATES.contains(current.getSQLState())
                    || current.getErrorCode() == FIREBIRD_NO_PRIVILEGE
                    || current.getErrorCode() == FIREBIRD_UNKNOWN_TABLE) {
                return true;
            }
        }
        return false;
    }

    /** Meses inteiramente dentro do período; {@code null} quando não há nenhum. */
    static CompleteMonths completeMonths(LocalDate start, LocalDate end) {
        YearMonth first = start.getDayOfMonth() == 1 ? YearMonth.from(start) : YearMonth.from(start).plusMonths(1);
        YearMonth last = end.equals(YearMonth.from(end).atEndOfMonth())
                ? YearMonth.from(end)
                : YearMonth.from(end).minusMonths(1);
        return first.isAfter(last) ? null : new CompleteMonths(first, last);
    }

    record CompleteMonths(YearMonth first, YearMonth last) { }

    private int largestBudget() {
        int largest = maxChartRecords;
        for (int limit : new int[] {
                maxEntryRecords, maxDailyTotalsRecords, maxMonthlyTotalsRecords, maxOriginCatalogRecords}) {
            largest = Math.max(largest, limit);
        }
        return largest;
    }

    private static BigDecimal requiredTotal(BigDecimal value) {
        if (value == null) {
            throw new QuestorUnexpectedSourceDataException();
        }
        return value;
    }

    private void requireValidQuestorEntry(
            String debitAccountId,
            String creditAccountId,
            BigDecimal amount,
            Set<String> chartAccountIds) {
        if (amount == null || amount.signum() < 0
                || (debitAccountId == null && creditAccountId == null)
                || (debitAccountId != null && debitAccountId.equals(creditAccountId))
                || (debitAccountId != null && !chartAccountIds.contains(debitAccountId))
                || (creditAccountId != null && !chartAccountIds.contains(creditAccountId))) {
            throw new QuestorUnexpectedSourceDataException();
        }
    }

    private String required(String value) {
        String normalized = optional(value);
        if (normalized == null) {
            throw new QuestorUnexpectedSourceDataException();
        }
        return normalized;
    }

    private String optional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
