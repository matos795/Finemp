package com.finemp.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.finemp.company.Company;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CompanyPermissionAuthorizationServiceTest {

    @Test
    void shouldNeverGrantUnsegmentedPermissionToARestrictedScope() {
        var repository = mock(CompanyAccessGrantRepository.class);
        var service = new CompanyPermissionAuthorizationService(repository);
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        var grant = new CompanyAccessGrant(new UserAccount("Synthetic", "scope@example.invalid", "hash"),
                new Company("Synthetic", "11111111000111"), CompanyAccessRole.VIEWER,
                CostCenterScope.RESTRICTED, Set.of(CompanyPermission.DRE_VIEW));
        when(repository.findByUserAccountIdAndCompanyId(userId, companyId)).thenReturn(Optional.of(grant));

        assertThat(service.hasPermission(userId, companyId, CompanyPermission.DRE_VIEW)).isFalse();
    }

    @Test
    void shouldPreserveExplicitPermissionChecksForAllScope() {
        var repository = mock(CompanyAccessGrantRepository.class);
        var service = new CompanyPermissionAuthorizationService(repository);
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        var grant = new CompanyAccessGrant(new UserAccount("Synthetic", "scope@example.invalid", "hash"),
                new Company("Synthetic", "11111111000111"), CompanyAccessRole.VIEWER,
                CostCenterScope.ALL, Set.of(CompanyPermission.DRE_VIEW));
        when(repository.findByUserAccountIdAndCompanyId(userId, companyId)).thenReturn(Optional.of(grant));

        assertThat(service.hasPermission(userId, companyId, CompanyPermission.DRE_VIEW)).isTrue();
        assertThat(service.hasPermission(userId, companyId, CompanyPermission.ACCOUNT_CLASSIFICATION_MANAGE)).isFalse();
    }
}
