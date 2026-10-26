package com.finemp.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class PlatformPermissionAuthorizationServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Test
    void shouldDelegatePlatformPermissionCheckToRepository() {
        UUID userAccountId = UUID.randomUUID();

        when(userAccountRepository.existsByIdAndPlatformPermission(
                userAccountId,
                PlatformPermission.ACCOUNTING_FIRM_CREATE))
                .thenReturn(true);

        PlatformPermissionAuthorizationService service = new PlatformPermissionAuthorizationService(
                userAccountRepository);

        assertThat(service.hasPermission(
                userAccountId,
                PlatformPermission.ACCOUNTING_FIRM_CREATE))
                .isTrue();
    }
}

