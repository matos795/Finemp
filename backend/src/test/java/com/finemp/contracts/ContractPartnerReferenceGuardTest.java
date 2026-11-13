package com.finemp.contracts;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ContractPartnerReferenceGuardTest {

    @Test
    void contractReferencePreventsPhysicalPartnerDeletion() {
        var contracts = mock(ContractRepository.class);
        var companyId = UUID.randomUUID();
        var partnerId = UUID.randomUUID();

        when(contracts.existsByCompanyIdAndBusinessPartnerId(companyId, partnerId))
                .thenReturn(true);

        var guard = new ContractPartnerReferenceGuard(contracts);

        assertTrue(guard.hasReferences(companyId, partnerId));
        verify(contracts).existsByCompanyIdAndBusinessPartnerId(companyId, partnerId);
    }
}
