package com.finemp.administration.firmaccess;

import com.finemp.access.AccountingFirmAccessGrant;
import com.finemp.access.AccountingFirmAccessGrantRepository;
import com.finemp.access.AccountingFirmPermission;
import com.finemp.access.AccountingFirmPermissionAuthorizationService;
import com.finemp.access.CompanyAccessGrantRepository;
import com.finemp.access.PlatformPermission;
import com.finemp.access.PlatformPermissionAuthorizationService;
import com.finemp.access.UserAccountRepository;
import com.finemp.accountingfirm.AccountingFirmRepository;
import com.finemp.administration.OrganizationAccessDeniedException;
import com.finemp.administration.PagedResponse;
import com.finemp.administration.SaasResourceNotFoundException;
import com.finemp.administration.users.UserAdministrationSummary;
import com.finemp.audit.AccessAuditAction;
import com.finemp.audit.AccessAuditRecorder;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FirmAccessAdministrationService {

    private final AccountingFirmAccessGrantRepository grants;
    private final AccountingFirmRepository firms;
    private final UserAccountRepository users;
    private final CompanyAccessGrantRepository companyGrants;
    private final PlatformPermissionAuthorizationService platformAuthorization;
    private final AccountingFirmPermissionAuthorizationService firmAuthorization;
    private final AccessAuditRecorder audit;

    public FirmAccessAdministrationService(AccountingFirmAccessGrantRepository grants, AccountingFirmRepository firms,
            UserAccountRepository users, CompanyAccessGrantRepository companyGrants,
            PlatformPermissionAuthorizationService platformAuthorization,
            AccountingFirmPermissionAuthorizationService firmAuthorization, AccessAuditRecorder audit) {
        this.grants = grants;
        this.firms = firms;
        this.users = users;
        this.companyGrants = companyGrants;
        this.platformAuthorization = platformAuthorization;
        this.firmAuthorization = firmAuthorization;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PagedResponse<UserAdministrationSummary> list(UUID actorId, UUID firmId, String search, int page, int size) {
        requireAccess(actorId, firmId);
        requireFirm(firmId);
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Invalid page.");
        var result = grants.searchMembers(firmId, search == null ? "" : search.trim(),
                PageRequest.of(page, size, Sort.by("userAccount.name").ascending().and(Sort.by("id"))));
        return PagedResponse.from(result, grant -> UserAdministrationSummary.from(grant.getUserAccount()));
    }

    @Transactional(readOnly = true)
    public FirmUserAccessResponse find(UUID actorId, UUID firmId, UUID userId) {
        requireAccess(actorId, firmId);
        requireFirm(firmId);
        var grant = grants.findByUserAccountIdAndAccountingFirmId(userId, firmId)
                .orElseThrow(() -> new SaasResourceNotFoundException("User access"));
        return response(firmId, grant);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public FirmUserAccessResponse replace(UUID actorId, UUID firmId, UUID userId, Set<AccountingFirmPermission> requested) {
        requireAccess(actorId, firmId);
        var firm = requireFirm(firmId);
        var user = users.findLockedById(userId).orElseThrow(() -> new SaasResourceNotFoundException("User access"));
        boolean platformAdministrator = requireAccess(actorId, firmId);
        var existing = grants.findByUserAccountIdAndAccountingFirmId(userId, firmId);
        if (existing.isEmpty() && !platformAdministrator) throw new SaasResourceNotFoundException("User access");
        Set<AccountingFirmPermission> permissions = Set.copyOf(requested);
        if (existing.isPresent() && existing.get().getPermissions().equals(permissions)) return response(firmId, existing.get());
        var grant = existing.orElseGet(() -> grants.save(new AccountingFirmAccessGrant(user, firm, Set.of())));
        grant.replacePermissions(permissions);
        audit.record(AccessAuditAction.FIRM_ACCESS_REPLACED, actorId, userId, firmId, null, permissions);
        return response(firmId, grant);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void revoke(UUID actorId, UUID firmId, UUID userId) {
        requireAccess(actorId, firmId);
        requireFirm(firmId);
        var user = users.findLockedById(userId);
        requireAccess(actorId, firmId);
        if (user.isEmpty()) return;
        var grant = grants.findByUserAccountIdAndAccountingFirmId(userId, firmId);
        if (grant.isEmpty()) return;
        if (companyGrants.existsWithinAccountingFirm(userId, firmId)) throw new FirmAccessConflictException();
        grants.delete(grant.get());
        audit.record(AccessAuditAction.FIRM_ACCESS_REVOKED, actorId, userId, firmId, null, Set.of());
    }

    private boolean requireAccess(UUID actorId, UUID firmId) {
        boolean platform = platformAuthorization.hasPermission(actorId, PlatformPermission.PLATFORM_USER_MANAGE);
        if (!platform && !firmAuthorization.hasPermission(actorId, firmId, AccountingFirmPermission.USER_ACCESS_MANAGE)) {
            throw new OrganizationAccessDeniedException();
        }
        return platform;
    }

    private com.finemp.accountingfirm.AccountingFirm requireFirm(UUID firmId) {
        return firms.findById(firmId).orElseThrow(() -> new SaasResourceNotFoundException("Accounting firm"));
    }

    private FirmUserAccessResponse response(UUID firmId, AccountingFirmAccessGrant grant) {
        return new FirmUserAccessResponse(firmId, UserAdministrationSummary.from(grant.getUserAccount()), grant.getPermissions());
    }
}
