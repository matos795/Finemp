package com.finemp.accounting.canonical.v2;

import java.util.Set;

public record SourceCapabilities(
        Set<AccountingDataset> datasets,
        AmountSignPolicy amountSignPolicy,
        boolean entryKeyScopedBySource) {

    public SourceCapabilities {
        datasets = datasets == null ? null : Set.copyOf(datasets);
    }
}
