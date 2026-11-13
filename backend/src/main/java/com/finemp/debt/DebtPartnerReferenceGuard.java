package com.finemp.debt;

import com.finemp.finance.partner.BusinessPartnerReferenceGuard;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Debt history keeps its creditor addressable even after partner deactivation. */
@Component
public class DebtPartnerReferenceGuard implements BusinessPartnerReferenceGuard {

    private final DebtAgreementRepository debts;

    public DebtPartnerReferenceGuard(DebtAgreementRepository debts) {
        this.debts = debts;
    }

    @Override
    public boolean hasReferences(UUID companyId, UUID partnerId) {
        return debts.existsByCompanyIdAndBusinessPartnerId(companyId, partnerId);
    }
}
