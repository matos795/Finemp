package com.finemp.administration.companyaccess;

import com.finemp.access.CompanyAccessRole;
import com.finemp.access.CompanyPermission;
import com.finemp.access.CostCenterScope;
import com.finemp.administration.users.UserAdministrationSummary;
import java.util.Set;
import java.util.UUID;

public record CompanyUserAccessResponse(UUID companyId, UserAdministrationSummary user,
                                        CompanyAccessRole role, CostCenterScope costCenterScope,
                                        Set<CompanyPermission> permissions) {
    public CompanyUserAccessResponse { permissions = Set.copyOf(permissions); }
}
