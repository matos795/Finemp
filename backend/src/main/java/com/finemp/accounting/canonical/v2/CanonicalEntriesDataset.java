package com.finemp.accounting.canonical.v2;

import java.time.LocalDate;
import java.util.List;

public record CanonicalEntriesDataset(
        LocalDate startDate,
        LocalDate endDate,
        List<CanonicalAccountingEntry> entries,
        List<CanonicalEntrySourceMetadata> metadata) {

    public CanonicalEntriesDataset {
        entries = entries == null ? null : List.copyOf(entries);
        metadata = metadata == null ? null : List.copyOf(metadata);
    }
}
