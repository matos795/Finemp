package com.finemp.accounting.v2read;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.finemp.accounting.canonical.v2.AccountingDataset;
import com.finemp.accounting.coverage.AccountingDatasetCoverageSnapshot;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Cobertura acumulada de um dataset: o que já foi importado ao menos uma vez,
 * não "sincronizado até". INTERVALS preserva lacunas; SNAPSHOT só tem o instante
 * de publicação, sem período artificial. Nunca derivada da última SyncRun.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record AccountingDatasetCoverageResponse(
        UUID accountingSourceId,
        AccountingDataset dataset,
        Kind kind,
        String establishmentCode,
        boolean available,
        LocalDate importedFrom,
        LocalDate importedTo,
        List<Interval> intervals,
        Instant publishedAt) {

    public enum Kind { INTERVALS, SNAPSHOT }

    public record Interval(LocalDate importedFrom, LocalDate importedTo) {}

    public AccountingDatasetCoverageResponse {
        intervals = List.copyOf(intervals);
    }

    static AccountingDatasetCoverageResponse from(AccountingDatasetCoverageSnapshot snapshot) {
        if (isSnapshotDataset(snapshot.dataset())) {
            return new AccountingDatasetCoverageResponse(snapshot.accountingSourceId(), snapshot.dataset(),
                    Kind.SNAPSHOT, snapshot.establishmentCode(), snapshot.publishedAt() != null,
                    null, null, List.of(), snapshot.publishedAt());
        }
        return new AccountingDatasetCoverageResponse(snapshot.accountingSourceId(), snapshot.dataset(),
                Kind.INTERVALS, snapshot.establishmentCode(), !snapshot.intervals().isEmpty(),
                snapshot.importedFrom(), snapshot.importedTo(),
                snapshot.intervals().stream()
                        .map(interval -> new Interval(interval.importedFrom(), interval.importedTo()))
                        .toList(),
                null);
    }

    static boolean isSnapshotDataset(AccountingDataset dataset) {
        return dataset == AccountingDataset.CHART || dataset == AccountingDataset.ORIGIN_CATALOG;
    }
}
