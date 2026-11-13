package com.finemp.contracts;

import com.finemp.finance.partner.BusinessPartnerReferenceGuard;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Contracts keep their partners addressable even after the partner is deactivated. */
@Component
public class ContractPartnerReferenceGuard implements BusinessPartnerReferenceGuard {

    private final ContractRepository contracts;

    public ContractPartnerReferenceGuard(ContractRepository contracts) {
        this.contracts = contracts;
    }

    @Override
    public boolean hasReferences(UUID companyId, UUID partnerId) {
        return contracts.existsByCompanyIdAndBusinessPartnerId(companyId, partnerId);
    }
}
