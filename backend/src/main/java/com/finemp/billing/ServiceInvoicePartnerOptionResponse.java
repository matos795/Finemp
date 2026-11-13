package com.finemp.billing;

import com.finemp.finance.partner.BusinessPartner;
import java.util.UUID;

public record ServiceInvoicePartnerOptionResponse(
        UUID id,
        String name) {

    static ServiceInvoicePartnerOptionResponse from(BusinessPartner partner) {
        String tradeName = partner.getProfile().tradeName();
        return new ServiceInvoicePartnerOptionResponse(
                partner.getId(),
                tradeName.isBlank() ? partner.getProfile().name() : tradeName);
    }
}
