package com.finemp.administration.companyaccess;

import static org.assertj.core.api.Assertions.assertThat;
import com.finemp.access.*;
import com.finemp.company.Company;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CompanyAccessStateTest {
    @Test
    void shouldCopyExplicitPermissionsAndNeverInferCapabilitiesFromTheRole() {
        var user = new UserAccount("Synthetic", "synthetic@example.invalid", "hash");
        var grant = new CompanyAccessGrant(user, new Company("Synthetic", "15151515000115"));
        var requested = new HashSet<>(Set.of(CompanyPermission.DRE_VIEW));
        grant.replaceAccess(CompanyAccessRole.ADMIN, CostCenterScope.ALL, requested);
        requested.clear();
        grant.replaceAccess(grant.getRole(), grant.getCostCenterScope(), grant.getPermissions());
        assertThat(grant.getPermissions()).containsExactly(CompanyPermission.DRE_VIEW);
        assertThat(grant.hasPermission(CompanyPermission.ACCOUNTING_MOVEMENT_VIEW)).isFalse();
        grant.replaceAccess(CompanyAccessRole.ADMIN, CostCenterScope.ALL, Set.of());
        assertThat(grant.getPermissions()).isEmpty();
        assertThat(user.getPlatformPermissions()).isEmpty();
    }
}
