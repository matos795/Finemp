package com.finemp.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ServiceInvoiceContractPartnerChangeGuardTest {

    @Test
    void linkedInvoiceBlocksContractPartnerReassignment() {
        var invoices = mock(ServiceInvoiceRepository.class);
        var company = UUID.randomUUID();
        var contract = UUID.randomUUID();

        when(invoices.existsByCompanyIdAndContractId(company, contract))
                .thenReturn(true);

        var guard = new ServiceInvoiceContractPartnerChangeGuard(invoices);

        assertThat(guard.blocksPartnerChange(company, contract)).isTrue();
        verify(invoices).existsByCompanyIdAndContractId(company, contract);
    }
}
