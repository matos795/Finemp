package com.finemp.contracts;

import com.finemp.access.CompanyPermission;
import com.finemp.access.CompanyPermissionAuthorizationService;
import com.finemp.administration.OrganizationAccessDeniedException;
import com.finemp.administration.PagedResponse;
import com.finemp.administration.SaasResourceNotFoundException;
import com.finemp.company.Company;
import com.finemp.company.CompanyRepository;
import com.finemp.finance.partner.BusinessPartner;
import com.finemp.finance.partner.BusinessPartnerRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ContractService {

    private final ContractRepository contracts;
    private final CompanyRepository companies;
    private final BusinessPartnerRepository partners;
    private final CompanyPermissionAuthorizationService permissions;
    private final ObjectProvider<ContractPartnerChangeGuard> partnerChangeGuards;

    public ContractService(
            ContractRepository contracts,
            CompanyRepository companies,
            BusinessPartnerRepository partners,
            CompanyPermissionAuthorizationService permissions,
            ObjectProvider<ContractPartnerChangeGuard> partnerChangeGuards) {
        this.contracts = contracts;
        this.companies = companies;
        this.partners = partners;
        this.permissions = permissions;
        this.partnerChangeGuards = partnerChangeGuards;
    }

    public PagedResponse<ContractResponse> list(
            UUID actor,
            UUID companyId,
            String search,
            Boolean active,
            int page,
            int size) {

        requireRead(actor, companyId);
        if (page < 0 || size < 1 || size > 100 || search != null && search.length() > 100) {
            throw invalid();
        }

        var pageable = PageRequest.of(
                page,
                size,
                Sort.by("values.description").ascending().and(Sort.by("id")));

        return PagedResponse.from(
                contracts.search(
                        companyId,
                        search == null ? "" : search.trim(),
                        active,
                        pageable),
                ContractResponse::from);
    }

    public PagedResponse<ContractPartnerOptionResponse> listPartnerOptions(
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
                ContractPartnerOptionResponse::from);
    }

    public ContractResponse find(UUID actor, UUID companyId, UUID contractId) {
        requireRead(actor, companyId);
        return ContractResponse.from(owned(companyId, contractId));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ContractResponse create(UUID actor, UUID companyId, ContractRequest request) {
        Company company = writeOwner(actor, companyId);
        if (request == null || request.expectedVersion() != null) throw invalid();

        BusinessPartner partner = partner(companyId, request.businessPartnerId());
        ContractValues values = values(request);

        return ContractResponse.from(
                contracts.saveAndFlush(new Contract(company, partner, values)));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ContractResponse update(
            UUID actor,
            UUID companyId,
            UUID contractId,
            ContractRequest request) {

        writeOwner(actor, companyId);
        if (request == null || request.expectedVersion() == null || request.expectedVersion() < 0) {
            throw invalid();
        }

        Contract contract = owned(companyId, contractId);
        if (contract.getVersion() != request.expectedVersion()) {
            throw new ContractConflictException();
        }

        BusinessPartner partner = partner(companyId, request.businessPartnerId());
        UUID currentPartnerId = contract.getBusinessPartner().getId();
        if (!currentPartnerId.equals(partner.getId())
                && partnerChangeGuards.orderedStream()
                        .anyMatch(guard -> guard.blocksPartnerChange(companyId, contractId))) {
            throw new ContractConflictException(
                    ContractConflictException.Reason.REFERENCED_PARTNER_CHANGE);
        }
        contract.replace(partner, values(request));
        contracts.flush();

        return ContractResponse.from(contract);
    }

    private Company writeOwner(UUID actor, UUID companyId) {
        requireManage(actor, companyId);
        Company company = companies.findLockedById(companyId)
                .orElseThrow(OrganizationAccessDeniedException::new);
        requireManage(actor, companyId);
        return company;
    }

    private Contract owned(UUID companyId, UUID contractId) {
        return contracts.findByIdAndCompanyId(contractId, companyId)
                .orElseThrow(() -> new SaasResourceNotFoundException("Contrato"));
    }

    private BusinessPartner partner(UUID companyId, UUID partnerId) {
        if (partnerId == null) throw invalid();
        return partners.findByIdAndCompanyId(partnerId, companyId)
                .orElseThrow(() -> new SaasResourceNotFoundException("Parceiro comercial"));
    }

    private ContractValues values(ContractRequest request) {
        try {
            return new ContractValues(
                    request.direction(),
                    request.description(),
                    request.startDate(),
                    request.endDate(),
                    request.billingDay(),
                    new BigDecimal(request.monthlyAmount()),
                    request.active());
        } catch (NumberFormatException exception) {
            throw invalid();
        }
    }

    private void requireRead(UUID actor, UUID companyId) {
        if (!permissions.hasFreshPermission(actor, companyId, CompanyPermission.CONTRACT_VIEW)
                && !permissions.hasFreshPermission(actor, companyId, CompanyPermission.CONTRACT_MANAGE)) {
            throw new OrganizationAccessDeniedException();
        }
    }

    private void requireManage(UUID actor, UUID companyId) {
        if (!permissions.hasFreshPermission(actor, companyId, CompanyPermission.CONTRACT_MANAGE)) {
            throw new OrganizationAccessDeniedException();
        }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Parâmetros de contrato inválidos.");
    }
}
