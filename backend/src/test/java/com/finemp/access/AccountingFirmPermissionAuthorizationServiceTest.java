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

        AccountingFirmPermissionAuthorizationService service = new AccountingFirmPermissionAuthorizationService(
                accountingFirmAccessGrantRepository);

        assertThat(service.hasPermission(
                userAccountId,
                accountingFirmId,
                AccountingFirmPermission.HIERARCHY_MANAGE))
                .isTrue();
    }
}

