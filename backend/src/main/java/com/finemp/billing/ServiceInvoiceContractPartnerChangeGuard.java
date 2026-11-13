package com.finemp.billing;

import com.finemp.contracts.ContractPartnerChangeGuard;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** A linked fiscal record fixes the contract/partner relationship represented by that record. */
@Component
public class ServiceInvoiceContractPartnerChangeGuard implements ContractPartnerChangeGuard {

    private final ServiceInvoiceRepository invoices;

    public ServiceInvoiceContractPartnerChangeGuard(ServiceInvoiceRepository invoices) {
        this.invoices = invoices;
    }

    @Override
    public boolean blocksPartnerChange(UUID companyId, UUID contractId) {
        return invoices.existsByCompanyIdAndContractId(companyId, contractId);
    }
}
