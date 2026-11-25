package com.finemp.integration.questor;

import com.finemp.accounting.canonical.v2.AccountingDataset;
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
import java.util.ArrayList;
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

    private final QuestorConnectionProvider provider;
    private final FirebirdQuestorCompanyIdentityReader identities;
    private final Clock clock;
    private final int maxChartRecords;
    private final int maxEntryRecords;
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
        if (maxChartRecords < 1 || maxChartRecords == Integer.MAX_VALUE
                || maxEntryRecords < 1 || maxEntryRecords == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid Questor v2 reader resource limit.");
        }
        if (timeoutSeconds < 1 || timeoutSeconds > 120) {
            throw new IllegalArgumentException("Invalid Questor v2 query timeout.");
        }
        this.provider = Objects.requireNonNull(provider);
        this.identities = Objects.requireNonNull(identities);
        this.clock = Objects.requireNonNull(clock);
        this.maxChartRecords = maxChartRecords;
        this.maxEntryRecords = maxEntryRecords;
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

            var batch = new CanonicalAccountingBatch(
                    CanonicalAccountingBatch.VERSION,
                    identity,
                    new SourceCapabilities(
                            Set.of(AccountingDataset.CHART, AccountingDataset.MOVEMENTS),
                            AmountSignPolicy.NON_NEGATIVE_WITH_SIDE,
                            true),
                    chart,
                    entries,
                    null,
                    null,
                    null,
                    null,
                    null);

            try {
                return CanonicalV2BatchValidator.validate(
                        batch,
                        request.startDate(),
                        request.endDate(),
                        Math.max(maxChartRecords, maxEntryRecords));
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
