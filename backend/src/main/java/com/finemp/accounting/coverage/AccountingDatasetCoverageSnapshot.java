package com.finemp.accounting.coverage;

import com.finemp.accounting.canonical.v2.AccountingDataset;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AccountingDatasetCoverageSnapshot(
        UUID accountingSourceId,
        AccountingDataset dataset,
        LocalDate importedFrom,
        LocalDate importedTo,
        List<Interval> intervals,
        String establishmentCode,
        Instant publishedAt) {

    public AccountingDatasetCoverageSnapshot {
        intervals = List.copyOf(intervals);
    }

    public AccountingDatasetCoverageSnapshot(
            UUID accountingSourceId,
            AccountingDataset dataset,
            LocalDate importedFrom,
            LocalDate importedTo,
            List<Interval> intervals) {
        this(accountingSourceId, dataset, importedFrom, importedTo, intervals, null, null);
    }

    public record Interval(LocalDate importedFrom, LocalDate importedTo) {
        public Interval {
            if (importedFrom == null || importedTo == null || importedTo.isBefore(importedFrom)) {
                throw new IllegalArgumentException("Invalid accounting dataset coverage interval.");
            }
        }
    }
}
