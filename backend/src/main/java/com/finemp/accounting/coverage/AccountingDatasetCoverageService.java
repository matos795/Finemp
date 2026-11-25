package com.finemp.accounting.coverage;

import com.finemp.accounting.canonical.v2.AccountingDataset;
import com.finemp.accounting.source.AccountingSourceRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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

    /**
     * Compatibility entrypoint for temporal source-wide coverage.
     * Snapshot datasets must use recordSnapshot explicitly.
     */
    @Transactional
    public AccountingDatasetCoverageSnapshot record(
            UUID accountingSourceId,
            AccountingDataset dataset,
            LocalDate importedFrom,
            LocalDate importedTo) {
        return recordInterval(accountingSourceId, dataset, null, importedFrom, importedTo);
    }

    @Transactional
    public AccountingDatasetCoverageSnapshot recordInterval(
            UUID accountingSourceId,
            AccountingDataset dataset,
            String establishmentCode,
            LocalDate importedFrom,
            LocalDate importedTo) {
        Objects.requireNonNull(accountingSourceId, "accountingSourceId");
        Objects.requireNonNull(dataset, "dataset");
        if (isSnapshotDataset(dataset)) {
            throw new IllegalArgumentException("Snapshot accounting dataset does not have interval coverage.");
        }
        validateInterval(importedFrom, importedTo);
        String scope = validateScope(establishmentCode);
        lockSource(accountingSourceId);

        var existing = scopedRows(accountingSourceId, dataset, scope);
        var current = existing.stream()
                .map(row -> {
                    if (row.getImportedFrom() == null || row.getImportedTo() == null) {
                        throw new IllegalStateException("Temporal accounting coverage has invalid persisted shape.");
                    }
                    return new AccountingDatasetCoverageSnapshot.Interval(
                            row.getImportedFrom(),
                            row.getImportedTo());
                })
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
                            scope,
                            interval.importedFrom(),
                            interval.importedTo(),
                            null))
                    .toList());
        }

        return intervalSnapshot(accountingSourceId, dataset, scope, merged);
    }

    @Transactional
    public AccountingDatasetCoverageSnapshot recordSnapshot(
            UUID accountingSourceId,
            AccountingDataset dataset,
            String establishmentCode,
            Instant publishedAt) {
        Objects.requireNonNull(accountingSourceId, "accountingSourceId");
        Objects.requireNonNull(dataset, "dataset");
        if (!isSnapshotDataset(dataset)) {
            throw new IllegalArgumentException("Temporal accounting dataset requires interval coverage.");
        }
        Objects.requireNonNull(publishedAt, "publishedAt");
        String scope = validateScope(establishmentCode);
        Instant normalizedPublishedAt = publishedAt.truncatedTo(ChronoUnit.MICROS);
        lockSource(accountingSourceId);

        var existing = scopedRows(accountingSourceId, dataset, scope);
        if (existing.size() > 1) {
            throw new IllegalStateException("Snapshot accounting coverage is duplicated.");
        }
        if (!existing.isEmpty()) {
            var row = existing.getFirst();
            if (row.getImportedFrom() != null || row.getImportedTo() != null || row.getPublishedAt() == null) {
                throw new IllegalStateException("Snapshot accounting coverage has invalid persisted shape.");
            }
            if (normalizedPublishedAt.equals(row.getPublishedAt())) {
                return snapshotCoverage(accountingSourceId, dataset, scope, normalizedPublishedAt);
            }
            coverage.deleteAllInBatch(existing);
        }

        coverage.saveAndFlush(new AccountingDatasetCoverage(
                accountingSourceId,
                dataset,
                scope,
                null,
                null,
                normalizedPublishedAt));

        return snapshotCoverage(accountingSourceId, dataset, scope, normalizedPublishedAt);
    }

    @Transactional(readOnly = true)
    public AccountingDatasetCoverageSnapshot read(
            UUID accountingSourceId,
            AccountingDataset dataset) {
        return read(accountingSourceId, dataset, null);
    }

    @Transactional(readOnly = true)
    public AccountingDatasetCoverageSnapshot read(
            UUID accountingSourceId,
            AccountingDataset dataset,
            String establishmentCode) {
        Objects.requireNonNull(accountingSourceId, "accountingSourceId");
        Objects.requireNonNull(dataset, "dataset");
        String scope = validateScope(establishmentCode);
        var rows = scopedRows(accountingSourceId, dataset, scope);

        if (isSnapshotDataset(dataset)) {
            if (rows.size() > 1) {
                throw new IllegalStateException("Snapshot accounting coverage is duplicated.");
            }
            if (rows.isEmpty()) {
                return snapshotCoverage(accountingSourceId, dataset, scope, null);
            }
            var row = rows.getFirst();
            if (row.getImportedFrom() != null || row.getImportedTo() != null || row.getPublishedAt() == null) {
                throw new IllegalStateException("Snapshot accounting coverage has invalid persisted shape.");
            }
            return snapshotCoverage(accountingSourceId, dataset, scope, row.getPublishedAt());
        }

        var intervals = rows.stream()
                .map(row -> {
                    if (row.getImportedFrom() == null || row.getImportedTo() == null) {
                        throw new IllegalStateException("Temporal accounting coverage has invalid persisted shape.");
                    }
                    return new AccountingDatasetCoverageSnapshot.Interval(
                            row.getImportedFrom(),
                            row.getImportedTo());
                })
                .toList();

        return intervalSnapshot(accountingSourceId, dataset, scope, intervals);
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

    private List<AccountingDatasetCoverage> scopedRows(
            UUID accountingSourceId,
            AccountingDataset dataset,
            String establishmentCode) {
        if (establishmentCode == null) {
            return coverage
                    .findAllByAccountingSourceIdAndDatasetAndEstablishmentCodeIsNullOrderByImportedFromAscImportedToAsc(
                            accountingSourceId,
                            dataset);
        }
        return coverage
                .findAllByAccountingSourceIdAndDatasetAndEstablishmentCodeOrderByImportedFromAscImportedToAsc(
                        accountingSourceId,
                        dataset,
                        establishmentCode);
    }

    private void lockSource(UUID accountingSourceId) {
        sources.findLockedForSyncById(accountingSourceId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown accounting source."));
    }

    private static boolean isSnapshotDataset(AccountingDataset dataset) {
        return dataset == AccountingDataset.CHART || dataset == AccountingDataset.ORIGIN_CATALOG;
    }

    private static String validateScope(String establishmentCode) {
        if (establishmentCode != null && establishmentCode.isBlank()) {
            throw new IllegalArgumentException("Invalid accounting coverage establishment scope.");
        }
        return establishmentCode;
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

    private static AccountingDatasetCoverageSnapshot intervalSnapshot(
            UUID accountingSourceId,
            AccountingDataset dataset,
            String establishmentCode,
            List<AccountingDatasetCoverageSnapshot.Interval> intervals) {
        if (intervals.isEmpty()) {
            return new AccountingDatasetCoverageSnapshot(
                    accountingSourceId,
                    dataset,
                    null,
                    null,
                    List.of(),
                    establishmentCode,
                    null);
        }
        return new AccountingDatasetCoverageSnapshot(
                accountingSourceId,
                dataset,
                intervals.getFirst().importedFrom(),
                intervals.getLast().importedTo(),
                intervals,
                establishmentCode,
                null);
    }

    private static AccountingDatasetCoverageSnapshot snapshotCoverage(
            UUID accountingSourceId,
            AccountingDataset dataset,
            String establishmentCode,
            Instant publishedAt) {
        return new AccountingDatasetCoverageSnapshot(
                accountingSourceId,
                dataset,
                null,
                null,
                List.of(),
                establishmentCode,
                publishedAt);
    }
}
