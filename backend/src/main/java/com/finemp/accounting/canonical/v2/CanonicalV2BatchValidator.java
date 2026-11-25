package com.finemp.accounting.canonical.v2;

import com.finemp.accounting.sync.SyncFailureCode;
import com.finemp.accounting.sync.execution.SyncPublicationRejectedException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDate;
import java.time.YearMonth;

public final class CanonicalV2BatchValidator {
    private CanonicalV2BatchValidator() {
    }

    public static CanonicalAccountingBatch validate(
            CanonicalAccountingBatch batch,
            LocalDate expectedStartDate,
            LocalDate expectedEndDate,
            int maxRecordsPerDataset) {
        if (batch == null
                || expectedStartDate == null
                || expectedEndDate == null
                || expectedStartDate.isAfter(expectedEndDate)
                || maxRecordsPerDataset < 1
                || batch.contractVersion() != CanonicalAccountingBatch.VERSION
                || batch.observedIdentity() == null
                || batch.capabilities() == null
                || batch.capabilities().datasets() == null
                || batch.capabilities().amountSignPolicy() == null) {
            throw invalid();
        }

        var capabilities = batch.capabilities();
        requireDatasetPresence(capabilities, AccountingDataset.CHART, batch.chart());
        requireDatasetPresence(capabilities, AccountingDataset.MOVEMENTS, batch.entries());
        requireDatasetPresence(capabilities, AccountingDataset.DAILY_TOTALS, batch.dailyTotals());
        requireDatasetPresence(capabilities, AccountingDataset.MONTHLY_TOTALS, batch.monthlyTotals());
        requireDatasetPresence(capabilities, AccountingDataset.ORIGIN_CATALOG, batch.originCatalog());
        requireDatasetPresence(capabilities, AccountingDataset.PARTICIPANTS, batch.participants());
        requireDatasetPresence(capabilities, AccountingDataset.COST_ALLOCATIONS, batch.costAllocations());

        Map<String, CanonicalAccountingAccount> accounts = validateChart(batch.chart(), maxRecordsPerDataset);
        Map<String, CanonicalAccountingEntry> entries = validateEntries(
                batch.entries(),
                capabilities.amountSignPolicy(),
                accounts,
                capabilities.datasets().contains(AccountingDataset.CHART),
                expectedStartDate,
                expectedEndDate,
                maxRecordsPerDataset);

        validateDailyTotals(
                batch.dailyTotals(),
                accounts,
                capabilities.datasets().contains(AccountingDataset.CHART),
                expectedStartDate,
                expectedEndDate,
                maxRecordsPerDataset);
        validateMonthlyTotals(
                batch.monthlyTotals(),
                accounts,
                capabilities.datasets().contains(AccountingDataset.CHART),
                expectedStartDate,
                expectedEndDate,
                maxRecordsPerDataset);
        validateOriginCatalog(batch.originCatalog(), maxRecordsPerDataset);
        validateParticipants(batch.participants(), entries, maxRecordsPerDataset);
        validateCostAllocations(batch.costAllocations(), entries, accounts,
                capabilities.datasets().contains(AccountingDataset.CHART), maxRecordsPerDataset);

        return batch;
    }

    private static Map<String, CanonicalAccountingAccount> validateChart(
            CanonicalChartSnapshot chart,
            int maxRecords) {
        Map<String, CanonicalAccountingAccount> accounts = new LinkedHashMap<>();
        if (chart == null) {
            return accounts;
        }
        if (chart.asOf() == null || chart.accounts() == null || chart.accounts().size() > maxRecords) {
            throw invalid();
        }

        for (var account : chart.accounts()) {
            if (account == null
                    || !identity(account.sourceAccountId())
                    || !identity(account.classificationCode())
                    || !text(account.description(), 255)
                    || account.codes() == null
                    || !identity(account.codes().accountType())
                    || !identity(account.codes().calculationType())
                    || !identity(account.codes().balanceNature())
                    || !identity(account.codes().balanceControl())
                    || !identity(account.codes().showInBalanceSheet())
                    || !optionalText(account.codes().nativeDreCode(), 255)
                    || !optionalText(account.codes().nativeDreAccount(), 255)) {
                throw invalid();
            }

            var previous = accounts.putIfAbsent(account.sourceAccountId(), account);
            if (previous != null && !previous.equals(account)) {
                throw conflict();
            }
        }
        return accounts;
    }

    private static Map<String, CanonicalAccountingEntry> validateEntries(
            CanonicalEntriesDataset dataset,
            AmountSignPolicy signPolicy,
            Map<String, CanonicalAccountingAccount> accounts,
            boolean chartDeclared,
            LocalDate expectedStartDate,
            LocalDate expectedEndDate,
            int maxRecords) {
        Map<String, CanonicalAccountingEntry> entries = new LinkedHashMap<>();
        if (dataset == null) {
            return entries;
        }
        if (dataset.startDate() == null
                || dataset.endDate() == null
                || !dataset.startDate().equals(expectedStartDate)
                || !dataset.endDate().equals(expectedEndDate)
                || dataset.entries() == null
                || dataset.entries().size() > maxRecords) {
            throw invalid();
        }

        for (var entry : dataset.entries()) {
            if (entry == null
                    || !identity(entry.sourceEntryKey())
                    || entry.accountingDate() == null
                    || entry.accountingDate().isBefore(expectedStartDate)
                    || entry.accountingDate().isAfter(expectedEndDate)
                    || !optionalIdentity(entry.debitAccountId())
                    || !optionalIdentity(entry.creditAccountId())
                    || (entry.debitAccountId() == null && entry.creditAccountId() == null)
                    || (entry.debitAccountId() != null && entry.debitAccountId().equals(entry.creditAccountId()))
                    || !optionalIdentity(entry.establishmentCode())
                    || !optionalText(entry.historyText(), 300)) {
                throw invalid();
            }

            validateAmount(entry.amount());
            if (signPolicy == AmountSignPolicy.NON_NEGATIVE_WITH_SIDE && entry.amount().signum() < 0) {
                throw invalid();
            }

            if (chartDeclared) {
                requireAccount(accounts, entry.debitAccountId());
                requireAccount(accounts, entry.creditAccountId());
            }

            var previous = entries.putIfAbsent(entry.sourceEntryKey(), entry);
            if (previous != null && !previous.equals(entry)) {
                throw conflict();
            }
        }

        validateMetadata(dataset.metadata(), entries, maxRecords);
        return entries;
    }

    private static void validateMetadata(
            List<CanonicalEntrySourceMetadata> metadata,
            Map<String, CanonicalAccountingEntry> entries,
            int maxRecords) {
        if (metadata == null) {
            return;
        }
        if (metadata.size() > maxRecords) {
            throw invalid();
        }

        Map<String, CanonicalEntrySourceMetadata> unique = new LinkedHashMap<>();
        for (var item : metadata) {
            if (item == null
                    || !identity(item.sourceEntryKey())
                    || !entries.containsKey(item.sourceEntryKey())
                    || !optionalText(item.historyCode(), 255)
                    || !optionalText(item.originCode(), 255)
                    || !optionalText(item.entryTypeCode(), 255)
                    || !optionalText(item.dataOriginCode(), 255)
                    || !optionalText(item.batchCode(), 255)
                    || !optionalText(item.documentNumber(), 40)
                    || !optionalText(item.operationGroupKey(), 255)
                    || !optionalText(item.transactionKey(), 255)
                    || !optionalText(item.originEstablishmentCode(), 255)) {
                throw invalid();
            }

            var previous = unique.putIfAbsent(item.sourceEntryKey(), item);
            if (previous != null && !previous.equals(item)) {
                throw conflict();
            }
        }
    }

    private static void validateDailyTotals(
            List<CanonicalAccountDailyDebitCreditTotals> totals,
            Map<String, CanonicalAccountingAccount> accounts,
            boolean chartDeclared,
            LocalDate start,
            LocalDate end,
            int maxRecords) {
        if (totals == null) {
            return;
        }
        if (totals.size() > maxRecords) {
            throw invalid();
        }

        Map<DailyTotalsIdentity, CanonicalAccountDailyDebitCreditTotals> unique = new LinkedHashMap<>();
        for (var total : totals) {
            if (total == null
                    || !identity(total.establishmentCode())
                    || !identity(total.sourceAccountId())
                    || total.day() == null
                    || total.day().isBefore(start)
                    || total.day().isAfter(end)
                    || !identity(total.entryTypeCode())) {
                throw invalid();
            }
            validateNonNegativeTotal(total.debitTotal());
            validateNonNegativeTotal(total.creditTotal());
            if (chartDeclared) {
                requireAccount(accounts, total.sourceAccountId());
            }

            var key = new DailyTotalsIdentity(
                    total.establishmentCode(),
                    total.sourceAccountId(),
                    total.day(),
                    total.entryTypeCode());
            var previous = unique.putIfAbsent(key, total);
            if (previous != null && !previous.equals(total)) {
                throw conflict();
            }
        }
    }

    private static void validateMonthlyTotals(
            List<CanonicalAccountMonthlyDebitCreditTotals> totals,
            Map<String, CanonicalAccountingAccount> accounts,
            boolean chartDeclared,
            LocalDate start,
            LocalDate end,
            int maxRecords) {
        if (totals == null) {
            return;
        }
        if (totals.size() > maxRecords) {
            throw invalid();
        }

        var firstMonth = YearMonth.from(start);
        var lastMonth = YearMonth.from(end);
        Map<MonthlyTotalsIdentity, CanonicalAccountMonthlyDebitCreditTotals> unique = new LinkedHashMap<>();
        for (var total : totals) {
            if (total == null
                    || !identity(total.establishmentCode())
                    || !identity(total.sourceAccountId())
                    || total.month() == null
                    || total.month().isBefore(firstMonth)
                    || total.month().isAfter(lastMonth)
                    || !identity(total.entryTypeCode())) {
                throw invalid();
            }
            validateNonNegativeTotal(total.debitTotal());
            validateNonNegativeTotal(total.creditTotal());
            if (chartDeclared) {
                requireAccount(accounts, total.sourceAccountId());
            }

            var key = new MonthlyTotalsIdentity(
                    total.establishmentCode(),
                    total.sourceAccountId(),
                    total.month(),
                    total.entryTypeCode());
            var previous = unique.putIfAbsent(key, total);
            if (previous != null && !previous.equals(total)) {
                throw conflict();
            }
        }
    }

    private static void validateOriginCatalog(
            List<CanonicalOriginCatalogEntry> catalog,
            int maxRecords) {
        if (catalog == null) {
            return;
        }
        if (catalog.size() > maxRecords) {
            throw invalid();
        }

        Map<String, CanonicalOriginCatalogEntry> unique = new LinkedHashMap<>();
        for (var item : catalog) {
            if (item == null || !identity(item.originCode()) || !text(item.description(), 255)) {
                throw invalid();
            }
            var previous = unique.putIfAbsent(item.originCode(), item);
            if (previous != null && !previous.equals(item)) {
                throw conflict();
            }
        }
    }

    private static void validateParticipants(
            List<CanonicalEntryParticipants> participants,
            Map<String, CanonicalAccountingEntry> entries,
            int maxRecords) {
        if (participants == null) {
            return;
        }
        if (participants.size() > maxRecords) {
            throw invalid();
        }

        Map<String, CanonicalEntryParticipants> unique = new LinkedHashMap<>();
        for (var item : participants) {
            if (item == null
                    || !identity(item.sourceEntryKey())
                    || !entries.containsKey(item.sourceEntryKey())
                    || !optionalIdentity(item.debitParticipantCode())
                    || !optionalIdentity(item.creditParticipantCode())) {
                throw invalid();
            }
            var previous = unique.putIfAbsent(item.sourceEntryKey(), item);
            if (previous != null && !previous.equals(item)) {
                throw conflict();
            }
        }
    }

    private static void validateCostAllocations(
            List<CanonicalEntryCostAllocation> allocations,
            Map<String, CanonicalAccountingEntry> entries,
            Map<String, CanonicalAccountingAccount> accounts,
            boolean chartDeclared,
            int maxRecords) {
        if (allocations == null) {
            return;
        }
        if (allocations.size() > maxRecords) {
            throw invalid();
        }

        Map<CostAllocationIdentity, CanonicalEntryCostAllocation> unique = new LinkedHashMap<>();
        for (var item : allocations) {
            if (item == null
                    || !identity(item.sourceEntryKey())
                    || !entries.containsKey(item.sourceEntryKey())
                    || !identity(item.sourceAccountId())
                    || !identity(item.costCenterCode())
                    || !identity(item.natureCode())) {
                throw invalid();
            }
            validateAmount(item.amount());
            if (chartDeclared) {
                requireAccount(accounts, item.sourceAccountId());
            }

            var key = new CostAllocationIdentity(
                    item.sourceEntryKey(),
                    item.sourceAccountId(),
                    item.costCenterCode(),
                    item.natureCode());
            var previous = unique.putIfAbsent(key, item);
            if (previous != null && !previous.equals(item)) {
                throw conflict();
            }
        }
    }

    private static void requireDatasetPresence(
            SourceCapabilities capabilities,
            AccountingDataset dataset,
            Object value) {
        boolean declared = capabilities.datasets().contains(dataset);
        if (declared != (value != null)) {
            throw invalid();
        }
    }

    private static void requireAccount(
            Map<String, CanonicalAccountingAccount> accounts,
            String accountId) {
        if (accountId != null && !accounts.containsKey(accountId)) {
            throw invalid();
        }
    }

    private static void validateNonNegativeTotal(BigDecimal amount) {
        validateAmount(amount);
        if (amount.signum() < 0) {
            throw invalid();
        }
    }

    private static void validateAmount(BigDecimal amount) {
        if (amount == null) {
            throw invalid();
        }
        try {
            var compact = amount.signum() == 0 ? BigDecimal.ZERO : amount.stripTrailingZeros();
            if (compact.scale() > 4 || (long) compact.precision() - compact.scale() > 15) {
                throw invalid();
            }
            compact.setScale(4, java.math.RoundingMode.UNNECESSARY);
        } catch (ArithmeticException unrepresentable) {
            throw invalid();
        }
    }

    private static boolean identity(String value) {
        return text(value, 255) && !value.isBlank();
    }

    private static boolean optionalIdentity(String value) {
        return value == null || identity(value);
    }

    private static boolean optionalText(String value, int maxCodePoints) {
        return value == null || value.codePointCount(0, value.length()) <= maxCodePoints;
    }

    private static boolean text(String value, int maxCodePoints) {
        return value != null && value.codePointCount(0, value.length()) <= maxCodePoints;
    }

    private static SyncPublicationRejectedException invalid() {
        return new SyncPublicationRejectedException(SyncFailureCode.INVALID_PAYLOAD);
    }

    private static SyncPublicationRejectedException conflict() {
        return new SyncPublicationRejectedException(SyncFailureCode.DATA_CONFLICT);
    }

    private record DailyTotalsIdentity(
            String establishmentCode,
            String sourceAccountId,
            LocalDate day,
            String entryTypeCode) {
    }

    private record MonthlyTotalsIdentity(
            String establishmentCode,
            String sourceAccountId,
            YearMonth month,
            String entryTypeCode) {
    }

    private record CostAllocationIdentity(
            String sourceEntryKey,
            String sourceAccountId,
            String costCenterCode,
            String natureCode) {
    }
}
