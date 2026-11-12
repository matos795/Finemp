package com.finemp.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.finemp.access.PlatformPermissionAuthorizationService;
import com.finemp.administration.OrganizationAccessDeniedException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class AuditEventQueryServiceTest {

    @Mock
    private AccessAuditEventRepository events;

    @Mock
    private PlatformPermissionAuthorizationService authorization;

    @Test
    void nonSystemAdminIsRejectedBeforeQueryingJournal() {
        UUID actorId = UUID.randomUUID();
        when(authorization.isSystemAdmin(actorId)).thenReturn(false);

        var service = new AuditEventQueryService(events, authorization);

        assertThatThrownBy(() -> service.listGlobal(actorId, 0, 50))
                .isInstanceOf(OrganizationAccessDeniedException.class);

        verify(events, never()).findAll(any(Pageable.class));
    }

    @Test
    void companyTrailQueriesOnlyRequestedCompanyWithStableDescendingOrder() {
        UUID actorId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();

        when(authorization.isSystemAdmin(actorId)).thenReturn(true);
        when(events.findByCompanyId(any(UUID.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        var service = new AuditEventQueryService(events, authorization);

        service.listCompany(actorId, companyId, 2, 25);

        var pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(events).findByCompanyId(
                org.mockito.ArgumentMatchers.eq(companyId),
                pageable.capture());

        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(25);
        assertThat(pageable.getValue().getSort().getOrderFor("occurredAt").isDescending()).isTrue();
        assertThat(pageable.getValue().getSort().getOrderFor("id").isDescending()).isTrue();
    }

    @Test
    void responseExposesMetadataButNotFreeFormAuditState() {
        AccessAuditEvent event = org.mockito.Mockito.mock(AccessAuditEvent.class);
        UUID id = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        UUID subject = UUID.randomUUID();
        UUID company = UUID.randomUUID();
        UUID resource = UUID.randomUUID();
        UUID command = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-11-12T18:00:00Z");

        when(event.getId()).thenReturn(id);
        when(event.getAction()).thenReturn(AccessAuditAction.FINANCIAL_PERIOD_CLOSED);
        when(event.getActorUserAccountId()).thenReturn(actor);
        when(event.getSubjectUserAccountId()).thenReturn(subject);
        when(event.getCompanyId()).thenReturn(company);
        when(event.getOccurredAt()).thenReturn(occurredAt);
        when(event.getResourceType()).thenReturn("FINANCIAL_PERIOD");
        when(event.getResourceId()).thenReturn(resource);
        when(event.getCommandId()).thenReturn(command);

        var response = AuditEventResponse.from(event);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.action()).isEqualTo(AccessAuditAction.FINANCIAL_PERIOD_CLOSED);
        assertThat(response.actorUserAccountId()).isEqualTo(actor);
        assertThat(response.subjectUserAccountId()).isEqualTo(subject);
        assertThat(response.companyId()).isEqualTo(company);
        assertThat(response.occurredAt()).isEqualTo(occurredAt);
        assertThat(response.resourceType()).isEqualTo("FINANCIAL_PERIOD");
        assertThat(response.resourceId()).isEqualTo(resource);
        assertThat(response.commandId()).isEqualTo(command);

        assertThat(AuditEventResponse.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain(
                        "beforeState",
                        "afterState",
                        "reason",
                        "permissionSnapshot");
    }
}
