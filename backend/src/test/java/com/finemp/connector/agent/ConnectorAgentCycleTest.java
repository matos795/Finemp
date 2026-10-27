package com.finemp.connector.agent;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.finemp.accounting.reading.*;
import com.finemp.accounting.source.AccountingSourceType;
import com.finemp.accounting.sync.*;
import com.finemp.accounting.sync.execution.SyncAttemptStatus;
import com.finemp.connector.completion.ConnectorWorkResult;
import com.finemp.connector.work.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;

class ConnectorAgentCycleTest {
    private final FinempConnectorClient client=mock(FinempConnectorClient.class);
    private final AccountingSourceSnapshotReader reader=mock(AccountingSourceSnapshotReader.class);
    private final Instant server=Instant.parse("2026-07-01T12:00:00Z");
    private final ConnectorWorkResponse work=work(300000);
    private final AccountingSourceSnapshot snapshot=new AccountingSourceSnapshot(new ExternalCompanyIdentity("57","52525252000152"),List.of());
    @Test void shouldReadTheClaimedContextAndPublishOnlyTheVerifiedSnapshot() {
        prepare(work);when(reader.readSnapshot(work.context())).thenReturn(snapshot);when(client.complete(work,snapshot)).thenReturn(receipt(SyncAttemptStatus.SUCCEEDED));
        try(var agent=new ConnectorAgentCycle(client,reader,Duration.ofSeconds(2))) {
            assertThat(agent.runOnce()).isEqualTo(ConnectorAgentCycle.Outcome.SUCCEEDED);
        }
        var order=inOrder(client,reader);order.verify(client).claim();order.verify(reader).readSnapshot(work.context());order.verify(client).complete(work,snapshot);
    }
    @Test void shouldReportObservedMismatchWithoutSendingAnyMovements() {
        prepare(work);when(reader.readSnapshot(work.context())).thenReturn(new AccountingSourceSnapshot(new ExternalCompanyIdentity("58",snapshot.identity().cnpj()),List.of()));
        when(client.fail(work,SyncFailureCode.IDENTITY_MISMATCH)).thenReturn(receipt(SyncAttemptStatus.FAILED));
        try(var agent=new ConnectorAgentCycle(client,reader,Duration.ofSeconds(2))) {assertThat(agent.runOnce()).isEqualTo(ConnectorAgentCycle.Outcome.FAILED);}
        verify(client).fail(work,SyncFailureCode.IDENTITY_MISMATCH);verify(client,never()).complete(any(),any());
    }
    @Test void shouldKeepConfigurationFailureSeparateFromTransientReadFailure() {
        prepare(work);when(reader.readSnapshot(work.context())).thenThrow(new AccountingIdentityConfigurationException());
        try(var agent=new ConnectorAgentCycle(client,reader,Duration.ofSeconds(2))) {
            assertThat(agent.runOnce()).isEqualTo(ConnectorAgentCycle.Outcome.HALTED);assertThat(agent.runOnce()).isEqualTo(ConnectorAgentCycle.Outcome.HALTED);
        }
        verify(client,never()).fail(any(),any());verify(client,never()).complete(any(),any());verify(client,times(1)).claim();
    }
    @Test void shouldReportTransientReadFailureWithOnlyTheAllowedCode() {
        prepare(work);when(reader.readSnapshot(work.context())).thenThrow(new IllegalStateException("synthetic private driver detail"));
        when(client.fail(work,SyncFailureCode.READ_FAILED)).thenReturn(receipt(SyncAttemptStatus.FAILED));
        try(var agent=new ConnectorAgentCycle(client,reader,Duration.ofSeconds(2))) {assertThat(agent.runOnce()).isEqualTo(ConnectorAgentCycle.Outcome.FAILED);}
        verify(client).fail(work,SyncFailureCode.READ_FAILED);verify(client,never()).complete(any(),any());
    }
    @Test void shouldResolveAnUncertainAttemptBeforeClaimingOrReplayingAnything() {
        prepare(work);when(reader.readSnapshot(work.context())).thenReturn(snapshot);when(client.complete(work,snapshot)).thenReturn(receipt(SyncAttemptStatus.RUNNING));
        when(client.result(work)).thenReturn(receipt(SyncAttemptStatus.SUCCEEDED));
        try(var agent=new ConnectorAgentCycle(client,reader,Duration.ofSeconds(2))) {
            assertThat(agent.runOnce()).isEqualTo(ConnectorAgentCycle.Outcome.PENDING);assertThat(agent.runOnce()).isEqualTo(ConnectorAgentCycle.Outcome.SUCCEEDED);
        }
        verify(client,times(1)).claim();verify(reader,times(1)).readSnapshot(any());verify(client,times(1)).complete(any(),any());verify(client).result(work);
    }
    @Test void shouldNotOverlapReadThatIgnoresInterruptionAfterItsBudgetExpires() throws Exception {
        prepare(work);var release=new CountDownLatch(1);var entered=new CountDownLatch(1);var exited=new CountDownLatch(1);
        when(reader.readSnapshot(any())).thenAnswer(call->{entered.countDown();try{while(release.getCount()>0){try{release.await();}catch(InterruptedException ignored){}}return snapshot;}finally{exited.countDown();}});
        when(client.fail(work,SyncFailureCode.RESOURCE_LIMIT)).thenReturn(receipt(SyncAttemptStatus.FAILED));
        try(var agent=new ConnectorAgentCycle(client,reader,Duration.ofMillis(100))) {
            assertThat(agent.runOnce()).isEqualTo(ConnectorAgentCycle.Outcome.FAILED);assertThat(entered.await(1,TimeUnit.SECONDS)).isTrue();
            assertThat(agent.runOnce()).isEqualTo(ConnectorAgentCycle.Outcome.BUSY);verify(client,times(1)).claim();
        }finally{release.countDown();assertThat(exited.await(2,TimeUnit.SECONDS)).isTrue();}
        verify(client,never()).complete(any(),any());
    }
    @Test void shouldStopPublicationWhenTheHeartbeatIsLostDuringRead() throws Exception {
        var shortWork=work(300);prepare(shortWork);var entered=new CountDownLatch(1);var beat=new CountDownLatch(1);
        when(reader.readSnapshot(any())).thenAnswer(call->{entered.countDown();new CountDownLatch(1).await();return snapshot;});
        when(client.heartbeat(shortWork)).thenAnswer(call->{beat.countDown();throw new ConnectorClientException(ConnectorClientException.Kind.CREDENTIAL,401,0);});
        try(var agent=new ConnectorAgentCycle(client,reader,Duration.ofSeconds(2))) {
            assertThat(agent.runOnce()).isEqualTo(ConnectorAgentCycle.Outcome.RETRY_LATER);
            assertThat(entered.await(1,TimeUnit.SECONDS)).isTrue();assertThat(beat.await(1,TimeUnit.SECONDS)).isTrue();
        }
        verify(client,never()).complete(any(),any());verify(client,never()).fail(any(),any());
    }
    @Test void shouldNotReadWhenTheSourceTypeIsNotConfiguredAndShouldStopAfterClose() {
        when(client.claim()).thenReturn(Optional.of(work));
        var agent=new ConnectorAgentCycle(client,reader,Duration.ofSeconds(2));
        assertThat(agent.runOnce()).isEqualTo(ConnectorAgentCycle.Outcome.HALTED);agent.close();
        assertThat(agent.runOnce()).isEqualTo(ConnectorAgentCycle.Outcome.STOPPED);verify(reader,never()).readSnapshot(any());
    }
    @Test void shouldConsumeClaimRoundTripFromTheRelativeLeaseAndIgnoreLocalCalendarClock() {
        var nanos=new AtomicLong();prepare(work);
        when(client.claim()).thenAnswer(call->{nanos.set(Duration.ofSeconds(301).toNanos());return Optional.of(work);});
        try(var agent=new ConnectorAgentCycle(client,reader,Duration.ofSeconds(2),nanos::get)) {
            assertThat(agent.runOnce()).isEqualTo(ConnectorAgentCycle.Outcome.RETRY_LATER);
        }
        verify(reader,never()).readSnapshot(any());verify(client,never()).complete(any(),any());
    }
    @Test void shouldRenewTheLeaseWhileReadingAndPublishAfterMultipleHeartbeats() {
        var shortWork=work(600);prepare(shortWork);var beats=new CountDownLatch(4);var number=new AtomicInteger();
        when(client.heartbeat(shortWork)).thenAnswer(call->{int n=number.incrementAndGet();beats.countDown();
            return new ConnectorLeaseResponse(shortWork.attemptId(),server.plusMillis(n*200L+600),server.plusMillis(n*200L));});
        when(reader.readSnapshot(any())).thenAnswer(call->{if(!beats.await(3,TimeUnit.SECONDS))throw new IllegalStateException("Synthetic heartbeat timeout");return snapshot;});
        when(client.complete(shortWork,snapshot)).thenReturn(receipt(shortWork,SyncAttemptStatus.SUCCEEDED));
        try(var agent=new ConnectorAgentCycle(client,reader,Duration.ofSeconds(4))) {
            assertThat(agent.runOnce()).isEqualTo(ConnectorAgentCycle.Outcome.SUCCEEDED);
        }
        assertThat(number.get()).isGreaterThanOrEqualTo(4);verify(client).complete(shortWork,snapshot);
    }
    @Test void shouldStopOnCloseDuringReadWithoutReportingFailureOrPublishing() throws Exception {
        prepare(work);var entered=new CountDownLatch(1);
        when(reader.readSnapshot(any())).thenAnswer(call->{entered.countDown();new CountDownLatch(1).await();return snapshot;});
        var caller=Executors.newSingleThreadExecutor();var agent=new ConnectorAgentCycle(client,reader,Duration.ofSeconds(4));
        try {
            var result=caller.submit(agent::runOnce);assertThat(entered.await(2,TimeUnit.SECONDS)).isTrue();
            assertThat(agent.runOnce()).isEqualTo(ConnectorAgentCycle.Outcome.BUSY);agent.close();
            assertThat(result.get(2,TimeUnit.SECONDS)).isEqualTo(ConnectorAgentCycle.Outcome.STOPPED);
        }finally{agent.close();caller.shutdownNow();}
        verify(client,never()).complete(any(),any());verify(client,never()).fail(any(),any());
    }
    @Test void shouldKeepPostSubmissionErrorsPendingAndRespectRateLimitBeforeTryingAgain() {
        prepare(work);when(reader.readSnapshot(any())).thenReturn(snapshot);var nanos=new AtomicLong();
        when(client.complete(work,snapshot)).thenThrow(new ConnectorClientException(ConnectorClientException.Kind.HTTP,429,12));
        when(client.result(work)).thenReturn(receipt(SyncAttemptStatus.SUCCEEDED));
        try(var agent=new ConnectorAgentCycle(client,reader,Duration.ofSeconds(2),nanos::get)) {
            assertThat(agent.runOnce()).isEqualTo(ConnectorAgentCycle.Outcome.PENDING);
            assertThat(agent.runOnce()).isEqualTo(ConnectorAgentCycle.Outcome.RETRY_LATER);verify(client,never()).result(any());
            nanos.set(Duration.ofSeconds(12).toNanos());assertThat(agent.runOnce()).isEqualTo(ConnectorAgentCycle.Outcome.SUCCEEDED);
        }
        verify(client,times(1)).claim();verify(client,times(1)).complete(any(),any());verify(client,never()).fail(any(),any());
    }
    private void prepare(ConnectorWorkResponse item){when(client.claim()).thenReturn(Optional.of(item));when(reader.sourceType()).thenReturn(AccountingSourceType.QUESTOR);}
    private ConnectorWorkResponse work(long leaseMillis){return new ConnectorWorkResponse(UUID.randomUUID(),UUID.randomUUID(),1,SyncRunMode.INITIAL,server.plusMillis(leaseMillis),server,
            new AccountingReadRequest(UUID.randomUUID(),UUID.randomUUID(),AccountingSourceType.QUESTOR,"57","52525252000152",LocalDate.of(2026,7,1),LocalDate.of(2026,7,31)));}
    private ConnectorWorkResult receipt(SyncAttemptStatus status){return receipt(work,status);}
    private ConnectorWorkResult receipt(ConnectorWorkResponse item,SyncAttemptStatus status){return new ConnectorWorkResult(item.attemptId(),item.runId(),1,true,status,
            status==SyncAttemptStatus.SUCCEEDED?SyncRunStatus.SUCCEEDED:SyncRunStatus.RUNNING,item.leaseExpiresAt(),null,null,null,
            status==SyncAttemptStatus.SUCCEEDED?0L:null,status==SyncAttemptStatus.SUCCEEDED?0L:null,status==SyncAttemptStatus.SUCCEEDED?0L:null);}
}
