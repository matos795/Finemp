package com.finemp.debt;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.finemp.access.CompanyPermission;
import com.finemp.access.CompanyPermissionAuthorizationService;
import com.finemp.administration.OrganizationAccessDeniedException;
import com.finemp.company.Company;
import com.finemp.company.CompanyRepository;
import com.finemp.finance.partner.BusinessPartner;
import com.finemp.finance.partner.BusinessPartnerRepository;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;

class DebtAgreementServiceTest {

    private final DebtAgreementRepository debts = mock(DebtAgreementRepository.class);
    private final CompanyRepository companies = mock(CompanyRepository.class);
    private final BusinessPartnerRepository partners = mock(BusinessPartnerRepository.class);
    private final CompanyPermissionAuthorizationService permissions =
            mock(CompanyPermissionAuthorizationService.class);

    private final DebtAgreementService service =
            new DebtAgreementService(debts, companies, partners, permissions);

    @Test
    void debtManageIsRequiredBeforeAnyCreateLookup() {
        var actor = UUID.randomUUID();
        var company = UUID.randomUUID();

        assertThatThrownBy(() -> service.create(
                actor,
                company,
                request(DebtOrigin.BANK, UUID.randomUUID(), null)))
                .isInstanceOf(OrganizationAccessDeniedException.class);

        verifyNoInteractions(companies, partners, debts);
    }

    @Test
    void staleVersionStopsMutationBeforePartnerLookup() {
        var actor = UUID.randomUUID();
        var company = UUID.randomUUID();
        var debtId = UUID.randomUUID();
        var debt = mock(DebtAgreement.class);

        when(permissions.hasFreshPermission(actor, company, CompanyPermission.DEBT_MANAGE))
                .thenReturn(true);
        when(companies.findLockedById(company)).thenReturn(Optional.of(mock(Company.class)));
        when(debts.findByIdAndCompanyId(debtId, company)).thenReturn(Optional.of(debt));
        when(debt.getVersion()).thenReturn(2L);

        assertThatThrownBy(() -> service.update(
                actor,
                company,
                debtId,
                request(DebtOrigin.BANK, UUID.randomUUID(), 1L)))
                .isInstanceOf(DebtConflictException.class);

        verifyNoInteractions(partners);
        verify(debt, never()).replace(any(), any());
    }

    @Test
    void taxOriginRejectsPartnerAndNeverQueriesPartnerRepository() {
        var actor = UUID.randomUUID();
        var companyId = UUID.randomUUID();
        var company = mock(Company.class);

        when(permissions.hasFreshPermission(actor, companyId, CompanyPermission.DEBT_MANAGE))
                .thenReturn(true);
        when(companies.findLockedById(companyId)).thenReturn(Optional.of(company));

        assertThatThrownBy(() -> service.create(
                actor,
                companyId,
                request(DebtOrigin.TAX, UUID.randomUUID(), null)))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(partners);
        verify(debts, never()).saveAndFlush(any());
    }

    @Test
    void nonTaxOriginRequiresSameCompanyPartner() {
        var actor = UUID.randomUUID();
        var companyId = UUID.randomUUID();
        var partnerId = UUID.randomUUID();
        var company = mock(Company.class);

        when(permissions.hasFreshPermission(actor, companyId, CompanyPermission.DEBT_MANAGE))
                .thenReturn(true);
        when(companies.findLockedById(companyId)).thenReturn(Optional.of(company));
        when(partners.findByIdAndCompanyId(partnerId, companyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(
                actor,
                companyId,
                request(DebtOrigin.SUPPLIER, partnerId, null)))
                .isInstanceOf(RuntimeException.class);

        verify(debts, never()).saveAndFlush(any());
    }

    @Test
    void debtViewerCanListWithoutManagePermission() {
        var actor = UUID.randomUUID();
        var companyId = UUID.randomUUID();

        when(permissions.hasFreshPermission(actor, companyId, CompanyPermission.DEBT_VIEW))
                .thenReturn(true);
        when(debts.search(eq(companyId), eq(DebtOrigin.BANK), eq(""), any()))
                .thenReturn(Page.empty());

        var result = service.list(actor, companyId, DebtOrigin.BANK, "", 0, 50);

        assertThat(result.items()).isEmpty();
        verify(permissions, never()).hasFreshPermission(
                actor,
                companyId,
                CompanyPermission.DEBT_MANAGE);
    }

    private static DebtAgreementRequest request(
            DebtOrigin origin,
            UUID partnerId,
            Long version) {
        return new DebtAgreementRequest(
                partnerId,
                origin,
                "Capital de giro",
                DebtAmortizationSystem.SAC,
                "1000.00",
                3,
                "3.00",
                DebtRateInputPeriod.MONTHLY,
                null,
                LocalDate.of(2026, 1, 31),
                version);
    }
}
