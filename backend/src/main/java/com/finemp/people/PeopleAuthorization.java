package com.finemp.people;

import com.finemp.access.CompanyPermission;
import com.finemp.access.CompanyPermissionAuthorizationService;
import com.finemp.administration.OrganizationAccessDeniedException;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class PeopleAuthorization {

    private final CompanyPermissionAuthorizationService permissions;

    PeopleAuthorization(CompanyPermissionAuthorizationService permissions) {
        this.permissions = permissions;
    }

    void requireRead(UUID actor, UUID companyId) {
        if (!permissions.hasFreshPermission(actor, companyId, CompanyPermission.EMPLOYEE_VIEW)
                && !permissions.hasFreshPermission(actor, companyId, CompanyPermission.EMPLOYEE_MANAGE)) {
            throw new OrganizationAccessDeniedException();
        }
    }

    void requireManage(UUID actor, UUID companyId) {
        if (!permissions.hasFreshPermission(actor, companyId, CompanyPermission.EMPLOYEE_MANAGE)) {
            throw new OrganizationAccessDeniedException();
        }
    }
}
