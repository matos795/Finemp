package com.finemp.planning;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.finemp.access.CompanyPermission;
import com.finemp.access.CompanyPermissionAuthorizationService;
import com.finemp.administration.OrganizationAccessDeniedException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlanningAuthorizationTest {

    @Mock
    CompanyPermissionAuthorizationService permissions;

    @Test
    void viewOrManageCanReadButOnlyManageCanWrite() {
        var actor = UUID.randomUUID();
        var company = UUID.randomUUID();
        var authorization = new PlanningAuthorization(permissions);

        when(permissions.hasFreshPermission(
                actor,
                company,
                CompanyPermission.PLANNING_VIEW))
                .thenReturn(true);

        assertDoesNotThrow(() ->
                authorization.requireRead(actor, company));

        assertThrows(
                OrganizationAccessDeniedException.class,
                () -> authorization.requireManage(actor, company));

        when(permissions.hasFreshPermission(
                actor,
                company,
                CompanyPermission.PLANNING_VIEW))
                .thenReturn(false);
        when(permissions.hasFreshPermission(
                actor,
                company,
                CompanyPermission.PLANNING_MANAGE))
                .thenReturn(true);

        assertDoesNotThrow(() ->
                authorization.requireRead(actor, company));
        assertDoesNotThrow(() ->
                authorization.requireManage(actor, company));
    }
}
