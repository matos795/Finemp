package com.finemp.finance.partner;

import java.util.UUID;
import java.time.Instant;

public record BusinessPartnerResponse(UUID id,UUID companyId,BusinessPartnerProfile profile,long version,Instant createdAt,Instant updatedAt) {
    static BusinessPartnerResponse from(BusinessPartner row) {
        return new BusinessPartnerResponse(row.getId(),row.getCompany().getId(),row.getProfile(),row.getVersion(),row.getCreatedAt(),row.getUpdatedAt());
    }
    @Override public String toString() {return "BusinessPartnerResponse[id="+id+"]";}
}
