package com.finemp.accounting.sync.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import com.finemp.accounting.reading.*;
import com.finemp.accounting.source.AccountingSourceType;
import com.finemp.accounting.sync.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SyncRunExecutorTest {
    private final SyncExecutionLifecycle lifecycle = mock(SyncExecutionLifecycle.class);
    private final AccountingCompanyIdentityReader identities = mock(AccountingCompanyIdentityReader.class);
    private final AccountingSourceReaderRegistry readers = mock(AccountingSourceReaderRegistry.class);
    private final SyncPublicationPort publication = mock(SyncPublicationPort.class);
    private final UUID runId = UUID.randomUUID();
    private final AccountingReadRequest request = new AccountingReadRequest(UUID.randomUUID(), UUID.randomUUID(),
            AccountingSourceType.QUESTOR, "synthetic", "26262626000126", LocalDate.of(2026,7,1), LocalDate.of(2026,7,31));
    private final ClaimedSyncRun claim = new ClaimedSyncRun(runId, 1, SyncRunMode.INITIAL, request);
    private final SyncRunExecutor executor = new SyncRunExecutor(lifecycle, identities, readers, publication);
    @BeforeEach void prepare() {
        when(lifecycle.claim(runId)).thenReturn(Optional.of(claim));
        when(lifecycle.failIfCurrent(eq(claim), any())).thenReturn(true);
        when(identities.readIdentity(request)).thenReturn(new ExternalCompanyIdentity("synthetic", "26.262.626/0001-26"));
        when(readers.readMovements(request)).thenReturn(List.of());
    }
    @Test void shouldVerifyIdentityBeforeReadingAndPublishing() {
        assertThat(executor.execute(runId)).isEqualTo(SyncExecutionOutcome.PUBLISHED);
        var order = inOrder(lifecycle, identities, readers, publication);
        order.verify(lifecycle).claim(runId); order.verify(identities).readIdentity(request);
        order.verify(readers).readMovements(request); order.verify(publication).publish(claim, List.of());
    }
    @Test void shouldNeverReadOrPublishWhenExternalIdentityDiffers() {
        when(identities.readIdentity(request)).thenReturn(new ExternalCompanyIdentity("synthetic", "27272727000127"));
        assertThat(executor.execute(runId)).isEqualTo(SyncExecutionOutcome.FAILED);
        verify(lifecycle).failIfCurrent(claim, SyncFailureCode.IDENTITY_MISMATCH);
        verifyNoInteractions(readers, publication);
    }
    @Test void shouldNeverPublishAfterReadFailureOrExposePrivateErrorToHistory() {
        when(readers.readMovements(request)).thenThrow(new IllegalStateException("synthetic private address"));
        assertThat(executor.execute(runId)).isEqualTo(SyncExecutionOutcome.FAILED);
        verify(lifecycle).failIfCurrent(claim, SyncFailureCode.READ_FAILED);
        verifyNoInteractions(publication);
    }
    @Test void shouldNotChangeNewerWorkWhenPublicationFenceIsStale() {
        doThrow(new StaleSyncClaimException()).when(publication).publish(claim, List.of());
        assertThat(executor.execute(runId)).isEqualTo(SyncExecutionOutcome.STALE);
        verify(lifecycle, never()).failIfCurrent(any(), any());
    }
    @Test void shouldRecordOnlySanitizedPublicationFailure() {
        doThrow(new IllegalStateException("synthetic private SQL detail")).when(publication).publish(claim, List.of());
        assertThat(executor.execute(runId)).isEqualTo(SyncExecutionOutcome.FAILED);
        verify(lifecycle).failIfCurrent(claim, SyncFailureCode.PUBLICATION_FAILED);
    }
    @Test void shouldPerformNoIoWhenAnotherWorkerAlreadyClaimedTheRun() {
        when(lifecycle.claim(runId)).thenReturn(Optional.empty());
        assertThat(executor.execute(runId)).isEqualTo(SyncExecutionOutcome.NOT_CLAIMED);
        verifyNoInteractions(identities, readers, publication);
    }
    @Test void shouldPropagateSanitizedPublicationRejectionWithoutTreatingItAsAnUnknownDatabaseFailure() {
        doThrow(new SyncPublicationRejectedException(SyncFailureCode.DATA_CONFLICT)).when(publication).publish(claim, List.of());
        assertThat(executor.execute(runId)).isEqualTo(SyncExecutionOutcome.FAILED);
        verify(lifecycle).failIfCurrent(claim, SyncFailureCode.DATA_CONFLICT);
    }
    @Test void shouldNeverPublishOrRetryAnOversizedReadAsIfItWereComplete() {
        when(readers.readMovements(request)).thenThrow(new AccountingReadLimitExceededException());
        assertThat(executor.execute(runId)).isEqualTo(SyncExecutionOutcome.FAILED);
        verify(lifecycle).failIfCurrent(claim, SyncFailureCode.RESOURCE_LIMIT);
        verifyNoInteractions(publication);
    }
}

