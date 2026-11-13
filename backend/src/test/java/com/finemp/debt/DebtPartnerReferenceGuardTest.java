package com.finemp.debt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class DebtPartnerReferenceGuardTest {

    @Test
    void referencedCreditorBlocksPhysicalPartnerDeletion() {
        var debts = mock(DebtAgreementRepository.class);
        var company = UUID.randomUUID();
        var partner = UUID.randomUUID();

        when(debts.existsByCompanyIdAndBusinessPartnerId(company, partner))
                .thenReturn(true);

        var guard = new DebtPartnerReferenceGuard(debts);

        assertThat(guard.hasReferences(company, partner)).isTrue();
        verify(debts).existsByCompanyIdAndBusinessPartnerId(company, partner);
    }
}
