package com.finemp.accounting.canonical.v2;

import java.math.BigDecimal;

public record CanonicalEntryCostAllocation(
        String sourceEntryKey,
        String sourceAccountId,
        String costCenterCode,
        String natureCode,
        BigDecimal amount) {
}
