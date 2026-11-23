package com.finemp.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class AccountingFirmPermissionAuthorizationServiceTest {

    @Mock
    private AccountingFirmAccessGrantRepository accountingFirmAccessGrantRepository;

    @Test
    void shouldDelegateAccountingFirmPermissionCheckToRepository() {
        UUID userAccountId = UUID.randomUUID();
        UUID accountingFirmId = UUID.randomUUID();

        when(accountingFirmAccessGrantRepository.existsByUserAccountIdAndAccountingFirmIdAndPermission(
                userAccountId,
                accountingFirmId,
                AccountingFirmPermission.HIERARCHY_MANAGE))
                .thenReturn(true);

        AccountingFirmPermissionAuthorizationService service = new AccountingFirmPermissionAuthorizationService(accountingFirmAccessGrantRepository, org.mockito.Mockito.mock(PlatformPermissionAuthorizationService.class));

        assertThat(service.hasPermission(
                userAccountId,
                accountingFirmId,
                AccountingFirmPermission.HIERARCHY_MANAGE))
                .isTrue();
    }

    @Test
    void shouldAcceptAnyOfTheReadPermissionsWithoutWideningToOtherPermissions() {
        UUID userAccountId = UUID.randomUUID();
        UUID accountingFirmId = UUID.randomUUID();

        when(accountingFirmAccessGrantRepository.existsByUserAccountIdAndAccountingFirmIdAndAnyPermission(
                userAccountId,
                accountingFirmId,
                AccountingFirmPermission.HIERARCHY_READ))
                .thenReturn(true);

        AccountingFirmPermissionAuthorizationService service = new AccountingFirmPermissionAuthorizationService(
                accountingFirmAccessGrantRepository,
                org.mockito.Mockito.mock(PlatformPermissionAuthorizationService.class));

        assertThat(service.hasAnyPermission(userAccountId, accountingFirmId, AccountingFirmPermission.HIERARCHY_READ))
                .isTrue();
        assertThat(service.hasPermission(userAccountId, accountingFirmId, AccountingFirmPermission.HIERARCHY_MANAGE))
                .isFalse();
    }

    @Test
    void readSetsContainViewAndManageOnly() {
        assertThat(AccountingFirmPermission.HIERARCHY_READ)
                .containsExactlyInAnyOrder(AccountingFirmPermission.HIERARCHY_VIEW, AccountingFirmPermission.HIERARCHY_MANAGE);
        assertThat(AccountingFirmPermission.ACCOUNTING_SOURCE_READ)
                .containsExactlyInAnyOrder(
                        AccountingFirmPermission.ACCOUNTING_SOURCE_VIEW,
                        AccountingFirmPermission.ACCOUNTING_SOURCE_MANAGE);
    }
}
