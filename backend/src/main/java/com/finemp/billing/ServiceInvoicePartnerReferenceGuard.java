package com.finemp.billing;

import com.finemp.finance.partner.BusinessPartnerReferenceGuard;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Fiscal history keeps its partner addressable even after deactivation. */
@Component
public class ServiceInvoicePartnerReferenceGuard implements BusinessPartnerReferenceGuard {

    private final ServiceInvoiceRepository invoices;

    public ServiceInvoicePartnerReferenceGuard(ServiceInvoiceRepository invoices) {
        this.invoices = invoices;
    }

    @Override
    public boolean hasReferences(UUID companyId, UUID partnerId) {
        return invoices.existsByCompanyIdAndBusinessPartnerId(companyId, partnerId);
    }
}
