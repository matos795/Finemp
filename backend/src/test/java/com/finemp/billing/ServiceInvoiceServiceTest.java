package com.finemp.billing;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.finemp.access.CompanyPermission;
import com.finemp.access.CompanyPermissionAuthorizationService;
import com.finemp.administration.OrganizationAccessDeniedException;
import com.finemp.company.CompanyRepository;
import com.finemp.contracts.ContractRepository;
import com.finemp.finance.partner.BusinessPartnerRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ServiceInvoiceServiceTest {

    private final ServiceInvoiceRepository invoices = mock(ServiceInvoiceRepository.class);
    private final CompanyRepository companies = mock(CompanyRepository.class);
    private final BusinessPartnerRepository partners = mock(BusinessPartnerRepository.class);
    private final ContractRepository contracts = mock(ContractRepository.class);
    private final CompanyPermissionAuthorizationService permissions =
            mock(CompanyPermissionAuthorizationService.class);

    private final ServiceInvoiceService service =
            new ServiceInvoiceService(
                    invoices,
                    companies,
                    partners,
                    contracts,
                    permissions);

    @Test
    void billingManageIsRequiredBeforeAnyCreateLookup() {
        var actor = UUID.randomUUID();
        var company = UUID.randomUUID();

        assertThatThrownBy(() -> service.create(
                actor,
                company,
                new ServiceInvoiceRequest(
                        UUID.randomUUID(),
                        null,
                        "2026-11",
                        "Serviço",
                        "100.00",
                        "0",
                        false,
                        "0",
                        "0",
                        "0",
                        "0",
                        "0",
                        null)))
                .isInstanceOf(OrganizationAccessDeniedException.class);

        verifyNoInteractions(companies, partners, contracts, invoices);
    }

    @Test
    void staleVersionStopsMutationBeforePartnerOrContractLookup() {
        var actor = UUID.randomUUID();
        var company = UUID.randomUUID();
        var invoiceId = UUID.randomUUID();
        var invoice = mock(ServiceInvoiceDraft.class);

        when(permissions.hasFreshPermission(actor, company, CompanyPermission.BILLING_MANAGE))
                .thenReturn(true);
        when(companies.findLockedById(company)).thenReturn(Optional.of(mock(com.finemp.company.Company.class)));
        when(invoices.findByIdAndCompanyId(invoiceId, company)).thenReturn(Optional.of(invoice));
        when(invoice.getVersion()).thenReturn(2L);

        assertThatThrownBy(() -> service.update(
                actor,
                company,
                invoiceId,
                new ServiceInvoiceRequest(
                        UUID.randomUUID(),
                        null,
                        "2026-11",
                        "Serviço",
                        "100.00",
                        "0",
                        false,
                        "0",
                        "0",
                        "0",
                        "0",
                        "0",
                        1L)))
                .isInstanceOf(ServiceInvoiceConflictException.class);

        verifyNoInteractions(partners, contracts);
        verify(invoice, never()).replace(any(), any(), any());
    }

    @Test
    void billingViewerCanReadEmptyMonthWithoutManagePermission() {
        var actor = UUID.randomUUID();
        var company = UUID.randomUUID();

        when(permissions.hasFreshPermission(actor, company, CompanyPermission.BILLING_VIEW))
                .thenReturn(true);
        when(invoices.search(eq(company), any(), isNull(), eq(""), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        var result = service.list(actor, company, "2026-11", null, "", 0, 50);

        assertThat(result.items()).isEmpty();
        verify(permissions, never()).hasFreshPermission(
                actor,
                company,
                CompanyPermission.BILLING_MANAGE);
    }
}
