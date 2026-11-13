package com.finemp.contracts;

import com.finemp.finance.partner.BusinessPartner;
import java.util.UUID;

/** Minimal partner projection exposed only for the contract editor. */
public record ContractPartnerOptionResponse(UUID id, String name) {

    static ContractPartnerOptionResponse from(BusinessPartner partner) {
        String tradeName = partner.getProfile().tradeName();
        String displayName = tradeName == null || tradeName.isBlank()
                ? partner.getProfile().name()
                : tradeName;
        return new ContractPartnerOptionResponse(partner.getId(), displayName);
    }
}
