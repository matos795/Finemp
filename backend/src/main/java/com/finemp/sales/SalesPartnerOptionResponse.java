package com.finemp.sales;

import com.finemp.finance.partner.BusinessPartner;
import java.util.UUID;

public record SalesPartnerOptionResponse(UUID id, String name) {
    static SalesPartnerOptionResponse from(BusinessPartner partner) {
        String tradeName = partner.getProfile().tradeName();
        return new SalesPartnerOptionResponse(
                partner.getId(),
                tradeName == null || tradeName.isBlank()
                        ? partner.getProfile().name()
                        : tradeName);
    }
}
