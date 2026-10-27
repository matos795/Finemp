package com.finemp.administration.companyaccess;

import com.finemp.access.*;
import com.finemp.administration.OrganizationAccessDeniedException;
import com.finemp.administration.PagedResponse;
import com.finemp.administration.SaasResourceNotFoundException;
import com.finemp.administration.users.UserAdministrationSummary;
import com.finemp.audit.AccessAuditAction;
import com.finemp.audit.AccessAuditRecorder;
import com.finemp.client.ClientRepository;
import com.finemp.company.Company;
import com.finemp.company.CompanyRepository;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompanyAccessAdministrationService {
    private final CompanyAccessGrantRepository grants;
    private final AccountingFirmAccessGrantRepository firmGrants;
    private final UserAccountRepository users;
    private final ClientRepository clients;
    private final CompanyRepository companies;
    private final PlatformPermissionAuthorizationService platformAuthorization;
    private final AccountingFirmPermissionAuthorizationService firmAuthorization;
    private final AccessAuditRecorder audit;

    public CompanyAccessAdministrationService(CompanyAccessGrantRepository grants,
            AccountingFirmAccessGrantRepository firmGrants, UserAccountRepository users,
            ClientRepository clients, CompanyRepository companies,
            PlatformPermissionAuthorizationService platformAuthorization,
            AccountingFirmPermissionAuthorizationService firmAuthorization, AccessAuditRecorder audit) {
        this.grants = grants;
        this.firmGrants = firmGrants;
        this.users = users;
        this.clients = clients;
        this.companies = companies;
        this.platformAuthorization = platformAuthorization;
        this.firmAuthorization = firmAuthorization;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PagedResponse<UserAdministrationSummary> list(UUID actor, UUID firm, UUID client, UUID company,
                                                        String search, int page, int size) {
        requireCompany(actor, firm, client, company);
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Invalid page.");
        return PagedResponse.from(grants.searchCompanyUsers(company, search == null ? "" : search.trim(),
                PageRequest.of(page, size)), UserAdministrationSummary::from);
    }

    @Transactional(readOnly = true)
    public CompanyUserAccessResponse find(UUID actor, UUID firm, UUID client, UUID company, UUID target) {
        requireCompany(actor, firm, client, company);
        return response(grants.findByUserAccountIdAndCompanyId(target, company)
                .orElseThrow(() -> new SaasResourceNotFoundException("Company access")));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CompanyUserAccessResponse replace(UUID actor, UUID firm, UUID client, UUID company, UUID target,
                                             SetCompanyAccessRequest request) {
        Company ownedCompany = requireCompany(actor, firm, client, company);
        var user = users.findLockedById(target).orElseThrow(() -> new SaasResourceNotFoundException("Company access"));
        requireAdministration(actor, firm);
        if (firmGrants.findByUserAccountIdAndAccountingFirmId(target, firm).isEmpty()) {
            throw new CompanyAccessConflictException("Associe explicitamente a identidade à contabilidade antes de conceder acesso à empresa.");
        }
        if (request.costCenterScope() != CostCenterScope.ALL) {
            throw new CompanyAccessConflictException("Escopo restrito exige filtragem efetiva por centro de custo, ainda indisponível.");
        }
        var existing = grants.findByUserAccountIdAndCompanyId(target, company);
        if (existing.isPresent() && existing.get().getRole() == request.role()
                && existing.get().getCostCenterScope() == request.costCenterScope()
                && existing.get().getPermissions().equals(request.permissions())) return response(existing.get());
        var grant = existing.orElseGet(() -> grants.save(new CompanyAccessGrant(user, ownedCompany)));
        grant.replaceAccess(request.role(), request.costCenterScope(), request.permissions());
        audit.record(AccessAuditAction.COMPANY_ACCESS_REPLACED, actor, target, firm, company, grant.getPermissions());
        return response(grant);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void revoke(UUID actor, UUID firm, UUID client, UUID company, UUID target) {
        requireCompany(actor, firm, client, company);
        if (users.findLockedById(target).isEmpty()) return;
        requireAdministration(actor, firm);
        var grant = grants.findByUserAccountIdAndCompanyId(target, company);
        if (grant.isEmpty()) return;
        grants.delete(grant.get());
        audit.record(AccessAuditAction.COMPANY_ACCESS_REVOKED, actor, target, firm, company, java.util.Set.of());
    }

    private Company requireCompany(UUID actor, UUID firm, UUID client, UUID company) {
        requireAdministration(actor, firm);
        clients.findByIdAndAccountingFirmId(client, firm).orElseThrow(() -> new SaasResourceNotFoundException("Client"));
        return companies.findByIdAndClientId(company, client).orElseThrow(() -> new SaasResourceNotFoundException("Company"));
    }

    private void requireAdministration(UUID actor, UUID firm) {
        if (!platformAuthorization.hasPermission(actor, PlatformPermission.PLATFORM_USER_MANAGE)
                && !firmAuthorization.hasPermission(actor, firm, AccountingFirmPermission.USER_ACCESS_MANAGE)) {
            throw new OrganizationAccessDeniedException();
        }
    }

    private CompanyUserAccessResponse response(CompanyAccessGrant grant) {
        return new CompanyUserAccessResponse(grant.getCompany().getId(), UserAdministrationSummary.from(grant.getUserAccount()),
                grant.getRole(), grant.getCostCenterScope(), grant.getPermissions());
    }
}
