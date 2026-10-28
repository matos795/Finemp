package com.finemp.finance.partner;

import java.util.UUID;

/** Operational usage adapters belong to the modules that persist those references. */
public interface BusinessPartnerReferenceGuard {
    boolean hasReferences(UUID companyId,UUID partnerId);
}
