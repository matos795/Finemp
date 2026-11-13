package com.finemp.billing;

import com.finemp.access.CompanyPermission;
import com.finemp.access.CompanyPermissionAuthorizationService;
import com.finemp.administration.OrganizationAccessDeniedException;
import com.finemp.administration.PagedResponse;
import com.finemp.administration.SaasResourceNotFoundException;
import com.finemp.company.Company;
import com.finemp.company.CompanyRepository;
import com.finemp.contracts.Contract;
import com.finemp.contracts.ContractRepository;
import com.finemp.finance.partner.BusinessPartner;
import com.finemp.finance.partner.BusinessPartnerRepository;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ServiceInvoiceService {

    private final ServiceInvoiceRepository invoices;
    private final CompanyRepository companies;
    private final BusinessPartnerRepository partners;
    private final ContractRepository contracts;
    private final CompanyPermissionAuthorizationService permissions;

    public ServiceInvoiceService(
            ServiceInvoiceRepository invoices,
            CompanyRepository companies,
            BusinessPartnerRepository partners,
            ContractRepository contracts,
            CompanyPermissionAuthorizationService permissions) {
        this.invoices = invoices;
        this.companies = companies;
        this.partners = partners;
        this.contracts = contracts;
        this.permissions = permissions;
    }

    public PagedResponse<ServiceInvoiceResponse> list(
            UUID actor,
            UUID companyId,
            String month,
            ServiceInvoiceStatus status,
            String search,
            int page,
            int size) {
        requireRead(actor, companyId);
        YearMonth competence = month(month);
        if (page < 0 || size < 1 || size > 100 || search != null && search.length() > 100) {
            throw invalid();
        }

        var pageable = PageRequest.of(
                page,
                size,
                Sort.by("updatedAt").descending().and(Sort.by("id")));

        return PagedResponse.from(
                invoices.search(
                        companyId,
                        competence.atDay(1),
                        status,
                        search == null ? "" : search.trim(),
                        pageable),
                ServiceInvoiceResponse::from);
    }

    public ServiceInvoiceResponse find(UUID actor, UUID companyId, UUID invoiceId) {
        requireRead(actor, companyId);
        return ServiceInvoiceResponse.from(owned(companyId, invoiceId));
    }

    public PagedResponse<ServiceInvoicePartnerOptionResponse> partnerOptions(
            UUID actor,
            UUID companyId,
            String search,
            int page,
            int size) {
        requireManage(actor, companyId);
        if (page < 0 || size < 1 || size > 100 || search != null && search.length() > 100) {
            throw invalid();
        }

        var pageable = PageRequest.of(
                page,
                size,
                Sort.by("displayNameKey").ascending().and(Sort.by("id")));

        return PagedResponse.from(
                partners.searchActiveLookup(
                        companyId,
                        search == null ? "" : search.trim(),
                        pageable),
                ServiceInvoicePartnerOptionResponse::from);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ServiceInvoiceResponse create(
            UUID actor,
            UUID companyId,
            ServiceInvoiceRequest request) {
        Company company = writeOwner(actor, companyId);
        if (request == null || request.expectedVersion() != null) throw invalid();

        BusinessPartner partner = partner(companyId, request.businessPartnerId());
        Contract contract = contract(companyId, request.contractId(), partner);
        var invoice = new ServiceInvoiceDraft(
                company,
                partner,
                contract,
                values(request));

        return ServiceInvoiceResponse.from(invoices.saveAndFlush(invoice));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ServiceInvoiceResponse update(
            UUID actor,
            UUID companyId,
            UUID invoiceId,
            ServiceInvoiceRequest request) {
        writeOwner(actor, companyId);
        if (request == null || request.expectedVersion() == null || request.expectedVersion() < 0) {
            throw invalid();
        }

        ServiceInvoiceDraft invoice = owned(companyId, invoiceId);
        requireVersion(invoice, request.expectedVersion());

        BusinessPartner partner = partner(companyId, request.businessPartnerId());
        Contract contract = contract(companyId, request.contractId(), partner);
        try {
            invoice.replace(partner, contract, values(request));
        } catch (IllegalStateException exception) {
            throw new ServiceInvoiceConflictException();
        }
        invoices.flush();

        return ServiceInvoiceResponse.from(invoice);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ServiceInvoiceResponse markIssued(
            UUID actor,
            UUID companyId,
            UUID invoiceId,
            ServiceInvoiceStatusRequest request) {
        writeOwner(actor, companyId);
        if (request == null || request.expectedVersion() == null || request.expectedVersion() < 0) {
            throw invalid();
        }

        ServiceInvoiceDraft invoice = owned(companyId, invoiceId);
        requireVersion(invoice, request.expectedVersion());

        try {
            invoice.markIssued(request.invoiceNumber());
            invoices.flush();
        } catch (IllegalStateException | DataIntegrityViolationException exception) {
            throw new ServiceInvoiceConflictException();
        }

        return ServiceInvoiceResponse.from(invoice);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ServiceInvoiceResponse cancel(
            UUID actor,
            UUID companyId,
            UUID invoiceId,
            ServiceInvoiceStatusRequest request) {
        writeOwner(actor, companyId);
        if (request == null || request.expectedVersion() == null || request.expectedVersion() < 0) {
            throw invalid();
        }

        ServiceInvoiceDraft invoice = owned(companyId, invoiceId);
        requireVersion(invoice, request.expectedVersion());
        invoice.cancel();
        invoices.flush();

        return ServiceInvoiceResponse.from(invoice);
    }

    private Company writeOwner(UUID actor, UUID companyId) {
        requireManage(actor, companyId);
        Company company = companies.findLockedById(companyId)
                .orElseThrow(OrganizationAccessDeniedException::new);
        requireManage(actor, companyId);
        return company;
    }

    private ServiceInvoiceDraft owned(UUID companyId, UUID invoiceId) {
        return invoices.findByIdAndCompanyId(invoiceId, companyId)
                .orElseThrow(() -> new SaasResourceNotFoundException("Nota fiscal"));
    }

    private BusinessPartner partner(UUID companyId, UUID partnerId) {
        if (partnerId == null) throw invalid();
        return partners.findByIdAndCompanyId(partnerId, companyId)
                .orElseThrow(() -> new SaasResourceNotFoundException("Parceiro comercial"));
    }

    private Contract contract(
            UUID companyId,
            UUID contractId,
            BusinessPartner partner) {
        if (contractId == null) return null;
        Contract contract = contracts.findByIdAndCompanyId(contractId, companyId)
                .orElseThrow(() -> new SaasResourceNotFoundException("Contrato"));
        if (!contract.getBusinessPartner().getId().equals(partner.getId())) {
            throw invalid();
        }
        return contract;
    }

    private static ServiceInvoiceDraftValues values(ServiceInvoiceRequest request) {
        try {
            return new ServiceInvoiceDraftValues(
                    month(request.competence()).atDay(1),
                    request.serviceText(),
                    decimal(request.grossAmount()),
                    decimal(request.issRate()),
                    request.issWithheld(),
                    decimal(request.irRate()),
                    decimal(request.pisRate()),
                    decimal(request.cofinsRate()),
                    decimal(request.csllRate()),
                    decimal(request.inssRate()));
        } catch (NumberFormatException exception) {
            throw invalid();
        }
    }

    private static BigDecimal decimal(String value) {
        if (value == null) throw invalid();
        return new BigDecimal(value.trim().replace(',', '.'));
    }

    private static YearMonth month(String value) {
        try {
            return YearMonth.parse(value);
        } catch (RuntimeException exception) {
            throw invalid();
        }
    }

    private static void requireVersion(ServiceInvoiceDraft invoice, long expectedVersion) {
        if (invoice.getVersion() != expectedVersion) {
            throw new ServiceInvoiceConflictException();
        }
    }

    private void requireRead(UUID actor, UUID companyId) {
        if (!permissions.hasFreshPermission(actor, companyId, CompanyPermission.BILLING_VIEW)
                && !permissions.hasFreshPermission(actor, companyId, CompanyPermission.BILLING_MANAGE)) {
            throw new OrganizationAccessDeniedException();
        }
    }

    private void requireManage(UUID actor, UUID companyId) {
        if (!permissions.hasFreshPermission(actor, companyId, CompanyPermission.BILLING_MANAGE)) {
            throw new OrganizationAccessDeniedException();
        }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Parâmetros de nota fiscal inválidos.");
    }
}
