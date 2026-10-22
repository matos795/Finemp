package com.finemp.security;

import com.finemp.access.CompanyAccessGrant;
import com.finemp.access.CompanyAccessRole;
import com.finemp.access.CompanyPermission;
import com.finemp.access.CostCenterScope;

import java.util.Set;
import java.util.UUID;

public record CurrentUserCompanyAccessResponse(
        UUID id,
        String name,
        CompanyAccessRole role,
        Set<CompanyPermission> permissions,
        CostCenterScope costCenterScope) {

    public static CurrentUserCompanyAccessResponse from(
            CompanyAccessGrant grant) {

        return new CurrentUserCompanyAccessResponse(
                grant.getCompany().getId(),
                grant.getCompany().getLegalName(),
                grant.getRole(),
                grant.getPermissions(),
                grant.getCostCenterScope());
    }
}