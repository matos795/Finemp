package com.finemp.connector.completion;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.finemp.accounting.reading.*;
import com.finemp.accounting.source.AccountingSourceType;
import com.finemp.accounting.sync.*;
import com.finemp.accounting.sync.execution.*;
import com.finemp.accounting.sync.publication.AccountingPublicationTransaction;
import com.finemp.connector.ConnectorIdentity;
import com.finemp.connector.work.ConnectorWorkNotCurrentException;
import java.io.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;

class ConnectorCompletionServiceTest {
    private final ConnectorCompletionContext context = mock(ConnectorCompletionContext.class);
    private final ConnectorBatchDecoder decoder = mock(ConnectorBatchDecoder.class);
    private final AccountingPublicationTransaction publication = mock(AccountingPublicationTransaction.class);
    private final SyncExecutionLifecycle lifecycle = mock(SyncExecutionLifecycle.class);
    private final ConnectorCompletionService service = new ConnectorCompletionService(context, decoder, publication, lifecycle);
    private final ConnectorIdentity identity = new ConnectorIdentity(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 0);
    private final UUID attemptId = UUID.randomUUID(), runId = UUID.randomUUID();
    private final InputStream input = new ByteArrayInputStream(new byte[0]);
    private final ClaimedSyncRun claim = new ClaimedSyncRun(runId, 1, SyncRunMode.INITIAL,
            new AccountingReadRequest(identity.accountingSourceId(), UUID.randomUUID(), AccountingSourceType.QUESTOR,
                    "synthetic", "47474747000147", LocalDate.of(2026,7,1), LocalDate.of(2026,7,31)),
            new SyncClaimAgent(identity.credentialId(), identity.version()));
    private final ConnectorBatch batch = new ConnectorBatch(new ExternalCompanyIdentity("synthetic", "47474747000147"), List.of());
    @BeforeEach void prepare() {
        when(context.currentClaim(identity, attemptId)).thenReturn(claim);
        when(decoder.decode(input)).thenReturn(batch);
        when(context.result(identity, attemptId)).thenReturn(receipt(SyncRunStatus.SUCCEEDED, null));
        when(lifecycle.failIfCurrent(eq(claim), any())).thenReturn(SyncFailureDisposition.TERMINAL_FAILURE);
    }
    @Test void shouldParseThenPublishAndReadTheAuthoritativeReceiptWithoutAcceptingClientCounters() {
        assertThat(service.complete(identity, attemptId, input).httpStatus()).isEqualTo(200);
        var order = inOrder(context, decoder, publication);
        order.verify(context).currentClaim(identity, attemptId); order.verify(decoder).decode(input);
        order.verify(publication).publishConnector(claim, batch.observedIdentity(), batch.movements());
        order.verify(context).result(identity, attemptId); verifyNoInteractions(lifecycle);
    }
    @Test void shouldLeaveTheRunUntouchedWhenTheEnvelopeCannotBeParsed() {
        when(decoder.decode(input)).thenThrow(new InvalidConnectorBatchException());
        assertThatThrownBy(() -> service.complete(identity, attemptId, input)).isInstanceOf(InvalidConnectorBatchException.class);
        verifyNoInteractions(publication, lifecycle); verify(context, never()).result(any(), any());
    }
    @Test void shouldNeverFailANewerAttemptAfterStalePublication() {
        doThrow(new StaleSyncClaimException()).when(publication).publishConnector(any(), any(), anyList());
        assertThatThrownBy(() -> service.complete(identity, attemptId, input)).isInstanceOf(ConnectorWorkNotCurrentException.class);
        verifyNoInteractions(lifecycle); verify(context, never()).result(any(), any());
    }
    @Test void shouldRecordTypedRejectionOnlyAfterPublicationFailedAndThenReadReceipt() {
        doThrow(new SyncPublicationRejectedException(SyncFailureCode.DATA_CONFLICT)).when(publication).publishConnector(any(), any(), anyList());
        when(context.result(identity, attemptId)).thenReturn(receipt(SyncRunStatus.FAILED, SyncFailureCode.DATA_CONFLICT));
        var result = service.complete(identity, attemptId, input);
        assertThat(result.httpStatus()).isEqualTo(422); assertThat(result.result().attemptFailureCode()).isEqualTo(SyncFailureCode.DATA_CONFLICT);
        var order = inOrder(publication, lifecycle, context);
        order.verify(context).currentClaim(identity, attemptId);
        order.verify(publication).publishConnector(claim, batch.observedIdentity(), batch.movements());
        order.verify(lifecycle).failIfCurrent(claim, SyncFailureCode.DATA_CONFLICT);
        order.verify(context).result(identity, attemptId);
    }
    @Test void shouldOfferRetryReceiptForSanitizedInfrastructureFailureAndNotExposePrivateText() {
        doThrow(new IllegalStateException("synthetic private SQL detail")).when(publication).publishConnector(any(), any(), anyList());
        when(lifecycle.failIfCurrent(claim, SyncFailureCode.PUBLICATION_FAILED)).thenReturn(SyncFailureDisposition.RETRY_SCHEDULED);
        when(context.result(identity, attemptId)).thenReturn(receipt(SyncRunStatus.RUNNING, SyncFailureCode.PUBLICATION_FAILED));
        var result = service.complete(identity, attemptId, input);
        assertThat(result.httpStatus()).isEqualTo(202); assertThat(result.toString()).doesNotContain("private", "SQL");
        verify(lifecycle).failIfCurrent(claim, SyncFailureCode.PUBLICATION_FAILED);
    }
    @Test void shouldRefuseAnAcknowledgementWhenFailureCouldNotBeRecordedAgainstTheCurrentClaim() {
        doThrow(new SyncPublicationRejectedException(SyncFailureCode.INVALID_PAYLOAD)).when(publication).publishConnector(any(), any(), anyList());
        when(lifecycle.failIfCurrent(claim, SyncFailureCode.INVALID_PAYLOAD)).thenReturn(SyncFailureDisposition.STALE);
        assertThatThrownBy(() -> service.complete(identity, attemptId, input)).isInstanceOf(ConnectorWorkNotCurrentException.class);
        verify(context, never()).result(any(), any());
    }
    @Test void shouldUseTheSameLifecycleForTheRestrictedSourceFailureReport() {
        when(decoder.decodeFailure(input)).thenReturn(SyncFailureCode.RESOURCE_LIMIT);
        when(context.result(identity, attemptId)).thenReturn(receipt(SyncRunStatus.FAILED, SyncFailureCode.RESOURCE_LIMIT));
        assertThat(service.reportFailure(identity, attemptId, input).httpStatus()).isEqualTo(422);
        verify(lifecycle).failIfCurrent(claim, SyncFailureCode.RESOURCE_LIMIT); verifyNoInteractions(publication);
        verify(decoder, never()).decode(any());
    }
    private ConnectorWorkResult receipt(SyncRunStatus status, SyncFailureCode failure) {
        boolean success = status == SyncRunStatus.SUCCEEDED;
        return new ConnectorWorkResult(attemptId, runId, 1, true, success ? SyncAttemptStatus.SUCCEEDED : SyncAttemptStatus.FAILED,
                status, Instant.parse("2026-07-01T12:05:00Z"), null, failure, status == SyncRunStatus.FAILED ? failure : null,
                success ? 0L : null, success ? 0L : null, success ? 0L : null);
    }
}
