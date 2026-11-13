package com.finemp.debt;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record DebtAgreementResponse(
        UUID id,
        UUID companyId,
        UUID businessPartnerId,
        String businessPartnerName,
        DebtOrigin origin,
        String description,
        DebtAmortizationSystem amortizationSystem,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal principalAmount,
        int installmentCount,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal inputRate,
        DebtRateInputPeriod inputRatePeriod,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal fixedInstallmentAmount,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal effectiveMonthlyRate,
        LocalDate firstDueDate,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    static DebtAgreementResponse from(DebtAgreement agreement) {
        var values = agreement.getValues();
        var partner = agreement.getBusinessPartner();

        String partnerName = null;
        UUID partnerId = null;
        if (partner != null) {
            partnerId = partner.getId();
            partnerName = partner.getProfile().tradeName().isBlank()
                    ? partner.getProfile().name()
                    : partner.getProfile().tradeName();
        }

        return new DebtAgreementResponse(
                agreement.getId(),
                agreement.getCompany().getId(),
                partnerId,
                partnerName,
                values.origin(),
                values.description(),
                values.amortizationSystem(),
                values.principalAmount(),
                values.installmentCount(),
                values.inputRate(),
                values.inputRatePeriod(),
                values.fixedInstallmentAmount(),
                values.effectiveMonthlyRate(),
                values.firstDueDate(),
                agreement.getVersion(),
                agreement.getCreatedAt(),
                agreement.getUpdatedAt());
    }
}
