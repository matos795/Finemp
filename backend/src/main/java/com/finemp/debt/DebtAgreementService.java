package com.finemp.debt;

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
import java.util.List;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DebtAgreementService {

    private final DebtAgreementRepository debts;
    private final CompanyRepository companies;
    private final BusinessPartnerRepository partners;
    private final CompanyPermissionAuthorizationService permissions;

    public DebtAgreementService(
            DebtAgreementRepository debts,
            CompanyRepository companies,
            BusinessPartnerRepository partners,
            CompanyPermissionAuthorizationService permissions) {
        this.debts = debts;
        this.companies = companies;
        this.partners = partners;
        this.permissions = permissions;
    }

    public PagedResponse<DebtAgreementResponse> list(
            UUID actor,
            UUID companyId,
            DebtOrigin origin,
            String search,
            int page,
            int size) {
        requireRead(actor, companyId);
        if (page < 0 || size < 1 || size > 100 || search != null && search.length() > 100) {
            throw invalid();
        }

        var pageable = PageRequest.of(
                page,
                size,
                Sort.by("values.firstDueDate").ascending().and(Sort.by("id")));

        return PagedResponse.from(
                debts.search(
                        companyId,
                        origin,
                        search == null ? "" : search.trim(),
                        pageable),
                DebtAgreementResponse::from);
    }

    public DebtAgreementResponse find(
            UUID actor,
            UUID companyId,
            UUID debtId) {
        requireRead(actor, companyId);
        return DebtAgreementResponse.from(owned(companyId, debtId));
    }

    public List<DebtInstallmentResponse> schedule(
            UUID actor,
            UUID companyId,
            UUID debtId) {
        requireRead(actor, companyId);
        return detailed(companyId, debtId)
                .getInstallments()
                .stream()
                .map(DebtInstallmentResponse::from)
                .toList();
    }

    public PagedResponse<DebtPartnerOptionResponse> partnerOptions(
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
                DebtPartnerOptionResponse::from);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DebtAgreementResponse create(
            UUID actor,
            UUID companyId,
            DebtAgreementRequest request) {
        Company company = writeOwner(actor, companyId);
        if (request == null || request.expectedVersion() != null) {
            throw invalid();
        }

        DebtAgreementValues values = values(request);
        BusinessPartner partner = partner(
                companyId,
                values.origin(),
                request.businessPartnerId());

        return DebtAgreementResponse.from(
                debts.saveAndFlush(new DebtAgreement(company, partner, values)));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DebtAgreementResponse update(
            UUID actor,
            UUID companyId,
            UUID debtId,
            DebtAgreementRequest request) {
        writeOwner(actor, companyId);
        if (request == null || request.expectedVersion() == null || request.expectedVersion() < 0) {
            throw invalid();
        }

        DebtAgreement debt = owned(companyId, debtId);
        if (debt.getVersion() != request.expectedVersion()) {
            throw new DebtConflictException();
        }

        DebtAgreementValues values = values(request);
        BusinessPartner partner = partner(
                companyId,
                values.origin(),
                request.businessPartnerId());

        debt.replace(partner, values);
        try {
            debts.flush();
        } catch (OptimisticLockingFailureException exception) {
            throw new DebtConflictException();
        }

        return DebtAgreementResponse.from(debt);
    }

    private Company writeOwner(UUID actor, UUID companyId) {
        requireManage(actor, companyId);
        Company company = companies.findLockedById(companyId)
                .orElseThrow(OrganizationAccessDeniedException::new);
        requireManage(actor, companyId);
        return company;
    }

    private DebtAgreement owned(UUID companyId, UUID debtId) {
        return debts.findByIdAndCompanyId(debtId, companyId)
                .orElseThrow(() -> new SaasResourceNotFoundException("Acordo de dívida"));
    }

    private DebtAgreement detailed(UUID companyId, UUID debtId) {
        return debts.findDetailed(companyId, debtId)
                .orElseThrow(() -> new SaasResourceNotFoundException("Acordo de dívida"));
    }

    private BusinessPartner partner(
            UUID companyId,
            DebtOrigin origin,
            UUID partnerId) {
        if (origin == DebtOrigin.TAX) {
            if (partnerId != null) throw invalid();
            return null;
        }

        if (partnerId == null) throw invalid();

        return partners.findByIdAndCompanyId(partnerId, companyId)
                .orElseThrow(() -> new SaasResourceNotFoundException("Credor"));
    }

    private static DebtAgreementValues values(DebtAgreementRequest request) {
        try {
            return new DebtAgreementValues(
                    request.origin(),
                    request.description(),
                    request.amortizationSystem(),
                    decimal(request.principalAmount()),
                    request.installmentCount(),
                    decimalOrNull(request.inputRate()),
                    request.inputRatePeriod(),
                    decimalOrNull(request.fixedInstallmentAmount()),
                    request.firstDueDate());
        } catch (NumberFormatException exception) {
            throw invalid();
        }
    }

    private static BigDecimal decimal(String value) {
        if (value == null) throw invalid();
        return new BigDecimal(value.trim().replace(',', '.'));
    }

    private static BigDecimal decimalOrNull(String value) {
        if (value == null || value.isBlank()) return null;
        return decimal(value);
    }

    private void requireRead(UUID actor, UUID companyId) {
        if (!permissions.hasFreshPermission(actor, companyId, CompanyPermission.DEBT_VIEW)
                && !permissions.hasFreshPermission(actor, companyId, CompanyPermission.DEBT_MANAGE)) {
            throw new OrganizationAccessDeniedException();
        }
    }

    private void requireManage(UUID actor, UUID companyId) {
        if (!permissions.hasFreshPermission(actor, companyId, CompanyPermission.DEBT_MANAGE)) {
            throw new OrganizationAccessDeniedException();
        }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Parâmetros de dívida inválidos.");
    }
}
