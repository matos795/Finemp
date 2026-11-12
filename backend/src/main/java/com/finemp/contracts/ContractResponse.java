package com.finemp.contracts;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ContractResponse(
        UUID id,
        UUID companyId,
        UUID businessPartnerId,
        String businessPartnerName,
        ContractDirection direction,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        int billingDay,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal monthlyAmount,
        boolean active,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    static ContractResponse from(Contract contract) {
        var partner = contract.getBusinessPartner();
        var values = contract.getValues();
        return new ContractResponse(
                contract.getId(),
                contract.getCompany().getId(),
                partner.getId(),
                partner.getProfile().name(),
                values.direction(),
                values.description(),
                values.startDate(),
                values.endDate(),
                values.billingDay(),
                values.monthlyAmount(),
                values.active(),
                contract.getVersion(),
                contract.getCreatedAt(),
                contract.getUpdatedAt());
    }
}
