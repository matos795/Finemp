package com.finemp.accounting.sync.control;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.finemp.access.*;
import com.finemp.accounting.source.*;
import com.finemp.accounting.sync.*;
import com.finemp.administration.OrganizationAccessDeniedException;
import java.time.*;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SyncControlServiceTest {
    private final AccountingSourceRepository sources = mock(AccountingSourceRepository.class);
    private final SyncRunRepository runs = mock(SyncRunRepository.class);
    private final SyncRunEventRepository events = mock(SyncRunEventRepository.class);
    private final CompanyPermissionAuthorizationService permissions = mock(CompanyPermissionAuthorizationService.class);
    private final SyncControlTransactionGuard guard = mock(SyncControlTransactionGuard.class);
    private final AccountingSource source = mock(AccountingSource.class);
    private final UUID actor = UUID.randomUUID(), sourceId = UUID.randomUUID(), companyId = UUID.randomUUID(), key = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-07-01T12:00:00Z");
    private final LocalDate start = LocalDate.of(2026, 7, 1), end = LocalDate.of(2026, 7, 31);
    private final SyncControlService service = new SyncControlService(sources, runs, events, permissions,
            Clock.fixed(now, ZoneOffset.UTC), 366, guard);

    @BeforeEach void prepare() {
        when(sources.findCompanyIdById(sourceId)).thenReturn(Optional.of(companyId));
        when(sources.findLockedForSyncById(sourceId)).thenReturn(Optional.of(source));
        when(permissions.hasFreshPermission(actor, companyId, CompanyPermission.QUESTOR_SYNC)).thenReturn(true);
        when(source.getId()).thenReturn(sourceId);
        when(source.getType()).thenReturn(AccountingSourceType.QUESTOR);
        when(source.isActive()).thenReturn(true);
        when(runs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test void shouldNeverLockOrWriteForUnauthorizedActor() {
        when(permissions.hasFreshPermission(actor, companyId, CompanyPermission.QUESTOR_SYNC)).thenReturn(false);
        assertThatThrownBy(() -> service.request(actor, sourceId, key, request())).isInstanceOf(OrganizationAccessDeniedException.class);
        verify(sources, never()).findLockedForSyncById(any());
        verifyNoInteractions(runs, events);
    }
    @Test void shouldReplayMatchingRequestWithoutNewEventEvenAfterWindowPolicyOrSourceStateChanges() {
        var oldEnd = start.plusDays(399);
        var existing = new SyncRun(source, actor, key, SyncRunMode.INITIAL, start, oldEnd, now);
        when(runs.findByAccountingSourceIdAndIdempotencyKey(sourceId, key)).thenReturn(Optional.of(existing));
        when(source.isActive()).thenReturn(false);
        assertThat(service.request(actor, sourceId, key, new RequestSyncRun(SyncRunMode.INITIAL, start, oldEnd)).endDate()).isEqualTo(oldEnd);
        verify(runs, never()).save(any());
        verifyNoInteractions(events);
    }
    @Test void shouldRejectChangedIdempotentRequestBeforeAnyPublication() {
        when(runs.findByAccountingSourceIdAndIdempotencyKey(sourceId, key)).thenReturn(Optional.of(
                new SyncRun(source, actor, key, SyncRunMode.INITIAL, start, end, now)));
        assertThatThrownBy(() -> service.request(actor, sourceId, key, new RequestSyncRun(SyncRunMode.RECONCILIATION, start, end)))
                .isInstanceOfSatisfying(SyncControlConflictException.class,
                        error -> assertThat(error.getReason()).isEqualTo(SyncControlConflictException.Reason.IDEMPOTENCY_MISMATCH));
        verify(runs, never()).save(any()); verifyNoInteractions(events);
    }
    @Test void shouldQueueOnlyIntentAndAuditTheSessionActor() {
        var response = service.request(actor, sourceId, key, request());
        assertThat(response.status()).isEqualTo(SyncRunStatus.REQUESTED);
        assertThat(response.requestedByUserId()).isEqualTo(actor);
        assertThat(response.readCount()).isNull();
        verify(events).save(argThat(event -> event.getStatus() == SyncRunStatus.REQUESTED && event.getActorId().equals(actor)));
        verify(guard).requireReadCommitted();
    }
    @Test void shouldTreat366DaysAsAResourceLimitAndReject367WithoutCreatingWork() {
        service.request(actor, sourceId, key, new RequestSyncRun(SyncRunMode.INITIAL, start, start.plusDays(365)));
        clearInvocations(runs, events);
        assertThatThrownBy(() -> service.request(actor, sourceId, UUID.randomUUID(),
                new RequestSyncRun(SyncRunMode.INITIAL, start, start.plusDays(366)))).isInstanceOf(IllegalArgumentException.class);
        verify(runs, never()).save(any()); verifyNoInteractions(events);
    }
    @Test void shouldKeepCancellationIdempotentWithoutAddingRepeatedHistory() {
        var run = new SyncRun(source, actor, key, SyncRunMode.INITIAL, start, end, now);
        run.cancel(now);
        var runId = UUID.randomUUID();
        when(runs.findByIdAndAccountingSourceId(runId, sourceId)).thenReturn(Optional.of(run));
        service.cancel(actor, sourceId, runId);
        verifyNoInteractions(events);
    }
    private RequestSyncRun request() { return new RequestSyncRun(SyncRunMode.INITIAL, start, end); }
}

