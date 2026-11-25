package com.finemp.accounting.canonical.v2;

import java.time.LocalDate;
import java.util.List;

public record CanonicalEntriesDataset(
        LocalDate startDate,
        LocalDate endDate,
        List<CanonicalAccountingEntry> entries,
        List<CanonicalEntrySourceMetadata> metadata,
        List<CanonicalObservedAccountDescriptor> observedAccountDescriptors) {

    public CanonicalEntriesDataset(
            LocalDate startDate,
            LocalDate endDate,
            List<CanonicalAccountingEntry> entries,
            List<CanonicalEntrySourceMetadata> metadata) {
        this(startDate, endDate, entries, metadata, null);
    }

    public CanonicalEntriesDataset {
        entries = entries == null ? null : List.copyOf(entries);
        metadata = metadata == null ? null : List.copyOf(metadata);
        observedAccountDescriptors =
                observedAccountDescriptors == null ? null : List.copyOf(observedAccountDescriptors);
    }
}
