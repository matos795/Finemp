package com.finemp.audit;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.finemp.access.PlatformPermissionAuthorizationService;
import com.finemp.administration.OrganizationAccessDeniedException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class AuditEventQueryServiceTest {

    @Mock
    AccessAuditEventRepository events;

    @Mock
    PlatformPermissionAuthorizationService platformAuthorization;

    @Test
    void nonSystemAdminCannotReadGlobalAudit() {
        var actorId = UUID.randomUUID();
        when(platformAuthorization.isSystemAdmin(actorId)).thenReturn(false);

        var service = new AuditEventQueryService(events, platformAuthorization);

        assertThrows(
                OrganizationAccessDeniedException.class,
                () -> service.listGlobal(actorId, 0, 50));

        verifyNoInteractions(events);
    }

    @Test
    void companyAuditIsFilteredAtRepositoryBoundary() {
        var actorId = UUID.randomUUID();
        var companyId = UUID.randomUUID();
        when(platformAuthorization.isSystemAdmin(actorId)).thenReturn(true);
        when(events.findByCompanyId(eq(companyId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        var service = new AuditEventQueryService(events, platformAuthorization);

        service.listCompany(actorId, companyId, 0, 50);

        verify(events).findByCompanyId(eq(companyId), any(Pageable.class));
    }
}
