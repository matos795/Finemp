package com.finemp.billing;

import java.util.UUID;

public record ServiceInvoiceRequest(
        UUID businessPartnerId,
        UUID contractId,
        String competence,
        String serviceText,
        String grossAmount,
        String issRate,
        boolean issWithheld,
        String irRate,
        String pisRate,
        String cofinsRate,
        String csllRate,
        String inssRate,
        Long expectedVersion) {
}
