package com.finemp.accounting.canonical.v2;

import java.time.Instant;
import java.util.List;

public record CanonicalChartSnapshot(
        Instant asOf,
        List<CanonicalAccountingAccount> accounts) {

    public CanonicalChartSnapshot {
        accounts = accounts == null ? null : List.copyOf(accounts);
    }
}
