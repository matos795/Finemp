package com.finemp.sales;

import com.finemp.access.CompanyPermission;
import com.finemp.access.CompanyPermissionAuthorizationService;
import com.finemp.administration.OrganizationAccessDeniedException;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class SalesAuthorization {

    private final CompanyPermissionAuthorizationService permissions;

    public SalesAuthorization(
            CompanyPermissionAuthorizationService permissions) {
        this.permissions = permissions;
    }

    public void requireRead(UUID actorId, UUID companyId) {
        if (!permissions.hasFreshPermission(
                        actorId,
                        companyId,
                        CompanyPermission.CRM_VIEW)
                && !permissions.hasFreshPermission(
                        actorId,
                        companyId,
                        CompanyPermission.CRM_MANAGE)) {
            throw new OrganizationAccessDeniedException();
        }
    }

    public void requireManage(UUID actorId, UUID companyId) {
        if (!permissions.hasFreshPermission(
                actorId,
                companyId,
                CompanyPermission.CRM_MANAGE)) {
            throw new OrganizationAccessDeniedException();
        }
    }
}
