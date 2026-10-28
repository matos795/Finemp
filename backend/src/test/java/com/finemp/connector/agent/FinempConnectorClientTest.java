package com.finemp.connector.agent;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.finemp.accounting.ingestion.ImportedAccountingMovement;
import com.finemp.accounting.movement.AccountingMovementSide;
import com.finemp.accounting.reading.*;
import com.finemp.accounting.source.AccountingSourceType;
import com.finemp.accounting.sync.*;
import com.finemp.accounting.sync.execution.SyncAttemptStatus;
import com.finemp.connector.completion.ConnectorBatchDecoder;
import com.finemp.connector.work.ConnectorWorkResponse;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FinempConnectorClientTest {
    private final UUID source=UUID.randomUUID(), attempt=UUID.randomUUID(), run=UUID.randomUUID();
    private final Instant now=Instant.parse("2026-07-01T12:00:00Z");
    private final ConnectorWorkResponse work=new ConnectorWorkResponse(attempt,run,1,SyncRunMode.INITIAL,now.plusSeconds(300),now,
            new AccountingReadRequest(source,UUID.randomUUID(),AccountingSourceType.QUESTOR,"0057","51515151000151",LocalDate.of(2026,7,1),LocalDate.of(2026,7,31)));
    private final ConnectorProtocolCodec codec=new ConnectorProtocolCodec(25000);
    @Test void shouldEncodeExactMoneyIsoDatesObservedIdentityAndLiteralLabelsWithoutRoundingOrDeduplication() {
        var row=row("key",new BigDecimal("999999999999999.9999"),"N/A",null);
        var bytes=codec.batch(work,new AccountingSourceSnapshot(new ExternalCompanyIdentity("57",work.context().expectedCompanyCnpj()),List.of(row,row)));
        var decoded=new ConnectorBatchDecoder(25000).decode(new ByteArrayInputStream(bytes));
        assertThat(decoded.observedIdentity().externalReference()).isEqualTo("57");
        assertThat(decoded.movements()).hasSize(2); assertThat(decoded.movements().getFirst().amount()).isEqualByComparingTo(row.amount());
        assertThat(decoded.movements().getFirst().accountingDate()).isEqualTo(LocalDate.of(2026,7,15));
        assertThat(decoded.movements().getFirst().accountKey()).isEqualTo("N/A"); assertThat(decoded.movements().getFirst().history()).isNull();
        assertThat(new String(bytes,StandardCharsets.UTF_8)).contains("999999999999999.9999").doesNotContain("E+");
    }
    @Test void shouldRefuseMismatchedIdentityExponentAndExcessivePrecisionBeforeSendingAnything() {
        var transport=mock(ConnectorHttpsTransport.class); var client=new FinempConnectorClient(transport,source,25000);
        for(var amount:List.of(new BigDecimal("1E+999999999"),new BigDecimal("0.00001"))) {
            assertThatThrownBy(()->client.complete(work,snapshot(List.of(row("key",amount,"0",null)))))
                    .isInstanceOf(ConnectorClientException.class).hasNoCause();
        }
        assertThatThrownBy(()->client.complete(work,new AccountingSourceSnapshot(new ExternalCompanyIdentity("58",work.context().expectedCompanyCnpj()),List.of())))
                .isInstanceOf(ConnectorClientException.class);
        verifyNoInteractions(transport);
    }
    @Test void shouldCheckLocalSourceBindingBeforeClaimingWork() {
        var transport=mock(ConnectorHttpsTransport.class); var client=new FinempConnectorClient(transport,source,25000);
        when(transport.exchange("GET","/session",null)).thenReturn(response(200,session(UUID.randomUUID())));
        assertThatThrownBy(client::claim).isInstanceOf(ConnectorClientException.class);
        verify(transport,never()).exchange(eq("POST"),eq("/work/claim"),any());
        when(transport.exchange("GET","/session",null)).thenReturn(response(200,session(source)));
        when(transport.exchange("POST","/work/claim",null)).thenReturn(response(204,"")); assertThat(client.claim()).isEmpty();
    }
    @Test void shouldAcceptInitialCredentialVersionZeroButRefuseMissingNegativeOrNonIntegralVersions() {
        String initial=session(source).replace("\"credentialVersion\":1","\"credentialVersion\":0");
        assertThat(codec.session(initial.getBytes(StandardCharsets.UTF_8),source).credentialVersion()).isZero();
        assertThatThrownBy(()->codec.session(initial.getBytes(StandardCharsets.UTF_8),UUID.randomUUID())).isInstanceOf(ConnectorClientException.class);
        for(var value:List.of("-1","0.5","9223372036854775808","null"))
            assertThatThrownBy(()->codec.session(initial.replace("\"credentialVersion\":0","\"credentialVersion\":"+value).getBytes(StandardCharsets.UTF_8),source))
                    .hasMessage("Connector client: PROTOCOL").hasNoCause();
        assertThatThrownBy(()->codec.session(initial.replace(",\"credentialVersion\":0","").getBytes(StandardCharsets.UTF_8),source)).hasMessage("Connector client: PROTOCOL");
        String invalidWork=tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(work).replace("\"attemptNumber\":1","\"attemptNumber\":0");
        assertThatThrownBy(()->codec.work(invalidWork.getBytes(StandardCharsets.UTF_8),source)).hasMessage("Connector client: PROTOCOL");
    }
    @Test void shouldResolveLostCompleteResponseThroughReceiptWithoutReplayingThePost() {
        var transport=mock(ConnectorHttpsTransport.class);var client=new FinempConnectorClient(transport,source,25000);
        when(transport.exchange(eq("POST"),eq(path("complete")),any())).thenThrow(new ConnectorClientException(ConnectorClientException.Kind.TIMEOUT));
        when(transport.exchange("GET",path("result"),null)).thenReturn(response(200,receipt(attempt,"SUCCEEDED","SUCCEEDED","0","0","0")));
        assertThat(client.complete(work,snapshot(List.of())).attemptStatus()).isEqualTo(SyncAttemptStatus.SUCCEEDED);
        verify(transport,times(1)).exchange(eq("POST"),eq(path("complete")),any());
        verify(transport).exchange("GET",path("result"),null);
    }
    @Test void shouldDecodeWorkAndRefuseUnknownModeOrMismatchedSourceContext() {
        var mapper=tools.jackson.databind.json.JsonMapper.builder().build();
        byte[] bytes=mapper.writeValueAsBytes(work);
        var decoded=codec.work(bytes,source);
        assertThat(decoded).isEqualTo(work);
        assertThatThrownBy(()->codec.work(bytes,UUID.randomUUID())).isInstanceOf(ConnectorClientException.class);
        String json=new String(bytes,StandardCharsets.UTF_8);
        assertThatThrownBy(()->codec.work(json.replace("INITIAL","UNKNOWN").getBytes(StandardCharsets.UTF_8),source))
                .isInstanceOf(ConnectorClientException.class);
    }
    @Test void shouldReturnExplicitRetryFailureAndResolveGatewayUncertaintyWithoutRepeatingPost() {
        var transport=mock(ConnectorHttpsTransport.class);var client=new FinempConnectorClient(transport,source,25000);
        when(transport.exchange(eq("POST"),eq(path("fail")),any())).thenReturn(response(202,receipt(attempt,"FAILED","RUNNING","null","null","null")));
        assertThat(client.fail(work,SyncFailureCode.READ_FAILED).attemptStatus()).isEqualTo(SyncAttemptStatus.FAILED);
        when(transport.exchange(eq("POST"),eq(path("complete")),any())).thenReturn(response(502,"private gateway diagnostic"));
        when(transport.exchange("GET",path("result"),null)).thenReturn(response(200,receipt(attempt,"SUCCEEDED","SUCCEEDED","0","0","0")));
        assertThat(client.complete(work,snapshot(List.of())).attemptStatus()).isEqualTo(SyncAttemptStatus.SUCCEEDED);
        verify(transport,times(1)).exchange(eq("POST"),eq(path("complete")),any());
        assertThatThrownBy(()->client.fail(work,SyncFailureCode.DATA_CONFLICT)).isInstanceOf(ConnectorClientException.class);
    }
    @Test void shouldNotTreatARunningReceiptOrAnotherAttemptsResultAsAnAcknowledgement() {
        var transport=mock(ConnectorHttpsTransport.class);var client=new FinempConnectorClient(transport,source,25000);
        when(transport.exchange(eq("POST"),eq(path("complete")),any())).thenReturn(response(409,"ignored private error"));
        when(transport.exchange("GET",path("result"),null)).thenReturn(response(200,receipt(attempt,"RUNNING","RUNNING","null","null","null")));
        assertThat(client.complete(work,snapshot(List.of())).attemptStatus()).isEqualTo(SyncAttemptStatus.RUNNING);
        when(transport.exchange("GET",path("result"),null)).thenReturn(response(200,receipt(UUID.randomUUID(),"SUCCEEDED","SUCCEEDED","0","0","0")));
        assertThatThrownBy(()->client.complete(work,snapshot(List.of()))).isInstanceOf(ConnectorClientException.class);
    }
    @Test void shouldRejectDuplicateTrailingMalformedEnumsAndInconsistentSuccessCounters() {
        String good=receipt(attempt,"SUCCEEDED","SUCCEEDED","1","1","0");
        for(var invalid:List.of(good+" {}",good.replace("\"attemptNumber\":1","\"attemptNumber\":1,\"attemptNumber\":1"),
                good.replace("SUCCEEDED","UNKNOWN"),receipt(attempt,"SUCCEEDED","SUCCEEDED","1","2","0"),
                receipt(attempt,"FAILED","SUCCEEDED","1","1","0")))
            assertThatThrownBy(()->codec.result(invalid.getBytes(StandardCharsets.UTF_8),work)).hasMessage("Connector client: PROTOCOL").hasNoCause();
    }
    @Test void shouldKeepFailureStatusAndRateLimitDistinctWithoutExposingErrorBody() {
        var transport=mock(ConnectorHttpsTransport.class);var client=new FinempConnectorClient(transport,source,25000);
        for(var status:List.of(401,429,503)) {
            when(transport.exchange("GET","/session",null)).thenReturn(new ConnectorHttpsTransport.Response(status,"synthetic private diagnostic".getBytes(StandardCharsets.UTF_8),12));
            assertThatThrownBy(client::session).isInstanceOfSatisfying(ConnectorClientException.class,ex->{
                assertThat(ex.status()).isEqualTo(status);assertThat(ex.retryAfterSeconds()).isEqualTo(12);
                assertThat(ex.getMessage()).doesNotContain("synthetic private");assertThat(ex.getCause()).isNull();
            });
        }
    }
    @Test void shouldRefuseOutboundByteBudgetEvenWhenRecordAndFieldBudgetsFit() {
        var rows=new ArrayList<ImportedAccountingMovement>();
        for(int i=0;i<10000;i++) rows.add(row("entry"+i,BigDecimal.ONE,"0","😀".repeat(255)));
        assertThatThrownBy(()->codec.batch(work,snapshot(rows))).isInstanceOfSatisfying(ConnectorClientException.class,
                ex->assertThat(ex.kind()).isEqualTo(ConnectorClientException.Kind.RESOURCE_LIMIT));
    }
    @Test void shouldCorrelateHeartbeatAndUseRelativeServerLeaseTimeWithoutLocalClockAssumptions() {
        String lease="{\"attemptId\":\""+attempt+"\",\"leaseExpiresAt\":\"2026-07-01T12:05:00Z\",\"serverTime\":\"2026-07-01T12:00:00Z\"}";
        var result=codec.lease(lease.getBytes(StandardCharsets.UTF_8),attempt);
        assertThat(Duration.between(result.serverTime(),result.leaseExpiresAt())).isEqualTo(Duration.ofSeconds(300));
        assertThatThrownBy(()->codec.lease(lease.getBytes(StandardCharsets.UTF_8),UUID.randomUUID())).isInstanceOf(ConnectorClientException.class);
    }
    private ImportedAccountingMovement row(String key,BigDecimal amount,String accountKey,String history) {
        return new ImportedAccountingMovement(key,LocalDate.of(2026,7,15),"synthetic-account",accountKey,"Synthetic account",AccountingMovementSide.DEBIT,amount,history);
    }
    private AccountingSourceSnapshot snapshot(List<ImportedAccountingMovement> rows) { return new AccountingSourceSnapshot(new ExternalCompanyIdentity("57",work.context().expectedCompanyCnpj()),rows); }
    private String path(String action) {return "/work/"+attempt+"/"+action;}
    private ConnectorHttpsTransport.Response response(int status,String body) {return new ConnectorHttpsTransport.Response(status,body.getBytes(StandardCharsets.UTF_8),0);}
    private String session(UUID id) {return "{\"credentialId\":\""+UUID.randomUUID()+"\",\"accountingFirmId\":\""+UUID.randomUUID()+"\",\"accountingSourceId\":\""+id+"\",\"credentialVersion\":1}";}
    private String receipt(UUID id,String attemptStatus,String runStatus,String read,String imported,String skipped) {
        return "{\"attemptId\":\""+id+"\",\"runId\":\""+run+"\",\"attemptNumber\":1,\"currentAttempt\":true,\"attemptStatus\":\""+attemptStatus
                +"\",\"runStatus\":\""+runStatus+"\",\"readCount\":"+read+",\"importedCount\":"+imported+",\"skippedCount\":"+skipped+"}";
    }
}
