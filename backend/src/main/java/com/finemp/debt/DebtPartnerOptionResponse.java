package com.finemp.debt;

import com.finemp.finance.partner.BusinessPartner;
import java.util.UUID;

public record DebtPartnerOptionResponse(UUID id, String name) {

    static DebtPartnerOptionResponse from(BusinessPartner partner) {
        String tradeName = partner.getProfile().tradeName();
        return new DebtPartnerOptionResponse(
                partner.getId(),
                tradeName.isBlank() ? partner.getProfile().name() : tradeName);
    }
}
