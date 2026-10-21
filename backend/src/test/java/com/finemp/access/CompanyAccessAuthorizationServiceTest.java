package com.finemp.access;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyAccessAuthorizationServiceTest {

    @Mock
    private CompanyAccessGrantRepository companyAccessGrantRepository;

    @Test
    void shouldAllowAccessWhenGrantExists() {

        UUID userAccountId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();

        when(companyAccessGrantRepository
                .existsByUserAccountIdAndCompanyId(
                        userAccountId,
                        companyId))
                .thenReturn(true);

        CompanyAccessAuthorizationService service = new CompanyAccessAuthorizationService(
                companyAccessGrantRepository);

        service.requireAccess(
                userAccountId,
                companyId);
    }

    @Test
    void shouldRejectAccessWhenGrantDoesNotExist() {

        UUID userAccountId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();

        when(companyAccessGrantRepository
                .existsByUserAccountIdAndCompanyId(
                        userAccountId,
                        companyId))
                .thenReturn(false);

        CompanyAccessAuthorizationService service = new CompanyAccessAuthorizationService(
                companyAccessGrantRepository);

        assertThatThrownBy(() -> service.requireAccess(
                userAccountId,
                companyId))
                .isInstanceOf(
                        CompanyAccessDeniedException.class);
    }
}