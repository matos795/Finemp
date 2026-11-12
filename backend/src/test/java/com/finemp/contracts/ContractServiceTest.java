package com.finemp.contracts;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.finemp.access.CompanyPermission;
import com.finemp.access.CompanyPermissionAuthorizationService;
import com.finemp.administration.OrganizationAccessDeniedException;
import com.finemp.company.Company;
import com.finemp.company.CompanyRepository;
import com.finemp.finance.partner.BusinessPartner;
import com.finemp.finance.partner.BusinessPartnerProfile;
import com.finemp.finance.partner.BusinessPartnerRepository;
import com.finemp.finance.partner.PartnerPersonType;
import com.finemp.finance.partner.PartnerRole;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContractServiceTest {

    @Mock ContractRepository contracts;
    @Mock CompanyRepository companies;
    @Mock BusinessPartnerRepository partners;
    @Mock CompanyPermissionAuthorizationService permissions;

    @Test
    void managePermissionIsRequiredForCreate() {
        var actor = UUID.randomUUID();
        var companyId = UUID.randomUUID();
        when(permissions.hasFreshPermission(actor, companyId, CompanyPermission.CONTRACT_MANAGE))
                .thenReturn(false);

        var service = service();

        assertThrows(
                OrganizationAccessDeniedException.class,
                () -> service.create(actor, companyId, request(UUID.randomUUID(), null)));

        verifyNoInteractions(companies, partners, contracts);
    }

    @Test
    void partnerMustBelongToSameCompany() {
        var actor = UUID.randomUUID();
        var companyId = UUID.randomUUID();
        var partnerId = UUID.randomUUID();
        var company = new Company("Empresa", "12345678000195");

        when(permissions.hasFreshPermission(actor, companyId, CompanyPermission.CONTRACT_MANAGE))
                .thenReturn(true);
        when(companies.findLockedById(companyId)).thenReturn(Optional.of(company));
        when(partners.findByIdAndCompanyId(partnerId, companyId)).thenReturn(Optional.empty());

        var service = service();

        assertThrows(
                RuntimeException.class,
                () -> service.create(actor, companyId, request(partnerId, null)));

        verify(contracts, never()).saveAndFlush(any());
    }

    @Test
    void staleVersionBlocksUpdateBeforeMutation() {
        var actor = UUID.randomUUID();
        var companyId = UUID.randomUUID();
        var contractId = UUID.randomUUID();
        var partnerId = UUID.randomUUID();
        var company = mock(Company.class);
        var contract = mock(Contract.class);

        when(permissions.hasFreshPermission(actor, companyId, CompanyPermission.CONTRACT_MANAGE))
                .thenReturn(true);
        when(companies.findLockedById(companyId)).thenReturn(Optional.of(company));
        when(contracts.findByIdAndCompanyId(contractId, companyId)).thenReturn(Optional.of(contract));
        when(contract.getVersion()).thenReturn(0L);

        var service = service();

        assertThrows(
                ContractConflictException.class,
                () -> service.update(actor, companyId, contractId, request(partnerId, 1L)));

        verify(partners, never()).findByIdAndCompanyId(any(), any());
        verify(contract, never()).replace(any(), any());
    }

    @Test
    void moneyRejectsFractionsOfCent() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ContractValues(
                        ContractDirection.INCOME,
                        "Mensalidade",
                        LocalDate.of(2026, 1, 1),
                        null,
                        10,
                        new java.math.BigDecimal("10.001"),
                        true));
    }

    private ContractService service() {
        return new ContractService(contracts, companies, partners, permissions);
    }

    private ContractRequest request(UUID partnerId, Long version) {
        return new ContractRequest(
                partnerId,
                ContractDirection.INCOME,
                "Mensalidade",
                LocalDate.of(2026, 1, 1),
                null,
                10,
                "100.00",
                true,
                version);
    }
}
