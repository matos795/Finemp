package com.finemp.finance.center;

import java.util.UUID;

public interface ResultCenterUsageGuard {
    boolean hasEntries(UUID companyId,UUID centerId);
    boolean hasReferences(UUID companyId,UUID centerId);
}
