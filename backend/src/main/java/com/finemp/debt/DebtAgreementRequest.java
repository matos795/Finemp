package com.finemp.debt;

import java.time.LocalDate;
import java.util.UUID;

public record DebtAgreementRequest(
        UUID businessPartnerId,
        DebtOrigin origin,
        String description,
        DebtAmortizationSystem amortizationSystem,
        String principalAmount,
        int installmentCount,
        String inputRate,
        DebtRateInputPeriod inputRatePeriod,
        String fixedInstallmentAmount,
        LocalDate firstDueDate,
        Long expectedVersion) {
}
