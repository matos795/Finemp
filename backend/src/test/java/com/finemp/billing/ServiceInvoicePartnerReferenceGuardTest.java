package com.finemp.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ServiceInvoicePartnerReferenceGuardTest {

    @Test
    void invoiceReferenceKeepsPartnerFromPhysicalDeletion() {
        var invoices = mock(ServiceInvoiceRepository.class);
        var company = UUID.randomUUID();
        var partner = UUID.randomUUID();

        when(invoices.existsByCompanyIdAndBusinessPartnerId(company, partner))
                .thenReturn(true);

        var guard = new ServiceInvoicePartnerReferenceGuard(invoices);

        assertThat(guard.hasReferences(company, partner)).isTrue();
        verify(invoices).existsByCompanyIdAndBusinessPartnerId(company, partner);
    }
}
