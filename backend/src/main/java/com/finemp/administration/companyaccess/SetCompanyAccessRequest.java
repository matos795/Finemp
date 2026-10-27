package com.finemp.administration.companyaccess;

import com.finemp.access.CompanyAccessRole;
import com.finemp.access.CompanyPermission;
import com.finemp.access.CostCenterScope;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

public record SetCompanyAccessRequest(@NotNull CompanyAccessRole role,
                                      @NotNull CostCenterScope costCenterScope,
                                      @NotNull Set<@NotNull CompanyPermission> permissions) { }
