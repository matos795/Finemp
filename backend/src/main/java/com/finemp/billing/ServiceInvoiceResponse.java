package com.finemp.billing;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ServiceInvoiceResponse(
        UUID id,
        UUID companyId,
        UUID businessPartnerId,
        String businessPartnerName,
        UUID contractId,
        String competence,
        String serviceText,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal grossAmount,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal issRate,
        boolean issWithheld,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal irRate,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal pisRate,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal cofinsRate,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal csllRate,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal inssRate,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal issAmount,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal irAmount,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal pisAmount,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal cofinsAmount,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal csllAmount,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal inssAmount,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalWithheld,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal netAmount,
        ServiceInvoiceStatus status,
        String invoiceNumber,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    static ServiceInvoiceResponse from(ServiceInvoiceDraft invoice) {
        var values = invoice.getValues();
        var taxes = values.taxes();
        var partner = invoice.getBusinessPartner();
        String partnerName = partner.getProfile().tradeName().isBlank()
                ? partner.getProfile().name()
                : partner.getProfile().tradeName();

        return new ServiceInvoiceResponse(
                invoice.getId(),
                invoice.getCompany().getId(),
                partner.getId(),
                partnerName,
                invoice.getContract() == null ? null : invoice.getContract().getId(),
                java.time.YearMonth.from(values.competence()).toString(),
                values.serviceText(),
                taxes.grossAmount(),
                values.issRate(),
                values.issWithheld(),
                values.irRate(),
                values.pisRate(),
                values.cofinsRate(),
                values.csllRate(),
                values.inssRate(),
                taxes.iss(),
                taxes.ir(),
                taxes.pis(),
                taxes.cofins(),
                taxes.csll(),
                taxes.inss(),
                taxes.totalWithheld(),
                taxes.netAmount(),
                invoice.getStatus(),
                invoice.getInvoiceNumber(),
                invoice.getVersion(),
                invoice.getCreatedAt(),
                invoice.getUpdatedAt());
    }
}
