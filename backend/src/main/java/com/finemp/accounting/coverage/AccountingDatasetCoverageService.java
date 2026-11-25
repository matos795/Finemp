package com.finemp.accounting.coverage;

import com.finemp.accounting.canonical.v2.AccountingDataset;
import com.finemp.accounting.source.AccountingSourceRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountingDatasetCoverageService {
    private final AccountingDatasetCoverageRepository coverage;
    private final AccountingSourceRepository sources;

    public AccountingDatasetCoverageService(
            AccountingDatasetCoverageRepository coverage,
            AccountingSourceRepository sources) {
        this.coverage = coverage;
        this.sources = sources;
    }

    @Transactional
    public AccountingDatasetCoverageSnapshot record(
            UUID accountingSourceId,
            AccountingDataset dataset,
            LocalDate importedFrom,
            LocalDate importedTo) {
        Objects.requireNonNull(accountingSourceId, "accountingSourceId");
        Objects.requireNonNull(dataset, "dataset");
        validateInterval(importedFrom, importedTo);

        sources.findLockedForSyncById(accountingSourceId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown accounting source."));

        var existing = coverage.findAllByAccountingSourceIdAndDatasetOrderByImportedFromAscImportedToAsc(
                accountingSourceId,
                dataset);
        var current = existing.stream()
                .map(row -> new AccountingDatasetCoverageSnapshot.Interval(
                        row.getImportedFrom(),
                        row.getImportedTo()))
                .toList();

        var withIncoming = new ArrayList<AccountingDatasetCoverageSnapshot.Interval>(current);
        withIncoming.add(new AccountingDatasetCoverageSnapshot.Interval(importedFrom, importedTo));
        var merged = merge(withIncoming);

        if (!current.equals(merged)) {
            if (!existing.isEmpty()) {
                coverage.deleteAllInBatch(existing);
            }
            coverage.saveAllAndFlush(merged.stream()
                    .map(interval -> new AccountingDatasetCoverage(
                            accountingSourceId,
                            dataset,
                            interval.importedFrom(),
                            interval.importedTo()))
                    .toList());
        }

        return snapshot(accountingSourceId, dataset, merged);
    }

    @Transactional(readOnly = true)
    public AccountingDatasetCoverageSnapshot read(
            UUID accountingSourceId,
            AccountingDataset dataset) {
        Objects.requireNonNull(accountingSourceId, "accountingSourceId");
        Objects.requireNonNull(dataset, "dataset");

        var intervals = coverage
                .findAllByAccountingSourceIdAndDatasetOrderByImportedFromAscImportedToAsc(
                        accountingSourceId,
                        dataset)
                .stream()
                .map(row -> new AccountingDatasetCoverageSnapshot.Interval(
                        row.getImportedFrom(),
                        row.getImportedTo()))
                .toList();

        return snapshot(accountingSourceId, dataset, intervals);
    }

    static List<AccountingDatasetCoverageSnapshot.Interval> merge(
            List<AccountingDatasetCoverageSnapshot.Interval> intervals) {
        if (intervals.isEmpty()) {
            return List.of();
        }

        var sorted = intervals.stream()
                .sorted(Comparator.comparing(AccountingDatasetCoverageSnapshot.Interval::importedFrom)
                        .thenComparing(AccountingDatasetCoverageSnapshot.Interval::importedTo))
                .toList();
        var merged = new ArrayList<AccountingDatasetCoverageSnapshot.Interval>();

        LocalDate currentFrom = sorted.getFirst().importedFrom();
        LocalDate currentTo = sorted.getFirst().importedTo();

        for (int index = 1; index < sorted.size(); index++) {
            var next = sorted.get(index);
            if (touchesOrOverlaps(currentTo, next.importedFrom())) {
                if (next.importedTo().isAfter(currentTo)) {
                    currentTo = next.importedTo();
                }
            } else {
                merged.add(new AccountingDatasetCoverageSnapshot.Interval(currentFrom, currentTo));
                currentFrom = next.importedFrom();
                currentTo = next.importedTo();
            }
        }

        merged.add(new AccountingDatasetCoverageSnapshot.Interval(currentFrom, currentTo));
        return List.copyOf(merged);
    }

    private static boolean touchesOrOverlaps(LocalDate currentTo, LocalDate nextFrom) {
        if (!nextFrom.isAfter(currentTo)) {
            return true;
        }
        return !currentTo.equals(LocalDate.MAX) && nextFrom.equals(currentTo.plusDays(1));
    }

    private static void validateInterval(LocalDate importedFrom, LocalDate importedTo) {
        if (importedFrom == null || importedTo == null || importedTo.isBefore(importedFrom)) {
            throw new IllegalArgumentException("Invalid accounting dataset coverage interval.");
        }
    }

    private static AccountingDatasetCoverageSnapshot snapshot(
            UUID accountingSourceId,
            AccountingDataset dataset,
            List<AccountingDatasetCoverageSnapshot.Interval> intervals) {
        if (intervals.isEmpty()) {
            return new AccountingDatasetCoverageSnapshot(
                    accountingSourceId,
                    dataset,
                    null,
                    null,
                    List.of());
        }
        return new AccountingDatasetCoverageSnapshot(
                accountingSourceId,
                dataset,
                intervals.getFirst().importedFrom(),
                intervals.getLast().importedTo(),
                intervals);
    }
}
