package com.finemp.planning;

import com.finemp.access.CompanyPermission;
import com.finemp.access.CompanyPermissionAuthorizationService;
import com.finemp.administration.OrganizationAccessDeniedException;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PlanningAuthorization {

    private final CompanyPermissionAuthorizationService permissions;

    public PlanningAuthorization(
            CompanyPermissionAuthorizationService permissions) {
        this.permissions = permissions;
    }

    public void requireRead(UUID actorId, UUID companyId) {
        if (!permissions.hasFreshPermission(
                        actorId,
                        companyId,
                        CompanyPermission.PLANNING_VIEW)
                && !permissions.hasFreshPermission(
                        actorId,
                        companyId,
                        CompanyPermission.PLANNING_MANAGE)) {
            throw new OrganizationAccessDeniedException();
        }
    }

    public void requireManage(UUID actorId, UUID companyId) {
        if (!permissions.hasFreshPermission(
                actorId,
                companyId,
                CompanyPermission.PLANNING_MANAGE)) {
            throw new OrganizationAccessDeniedException();
        }
    }
}
