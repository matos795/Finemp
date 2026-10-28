package com.finemp.connector.agent;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.finemp.TestcontainersConfiguration;
import com.finemp.access.*;
import com.finemp.accounting.ingestion.ImportedAccountingMovement;
import com.finemp.accounting.movement.AccountingMovementSide;
import com.finemp.accounting.reading.*;
import com.finemp.accounting.source.*;
import com.finemp.accounting.sync.*;
import com.finemp.accounting.sync.control.*;
import com.finemp.accountingfirm.*;
import com.finemp.client.*;
import com.finemp.company.*;
import com.finemp.connector.*;
import com.finemp.connector.work.ConnectorWorkService;
import com.finemp.accounting.sync.execution.SyncAttemptRepository;
import java.io.*;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.net.ssl.SSLParameters;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Real protocol/controller/security/transactions/PostgreSQL. MockMvc bridge is not a claim of real TLS/Firebird homologation. */
@SpringBootTest(properties={"finemp.connector.enabled=true","finemp.connector.protocol.enabled=true","finemp.sync.control.enabled=true"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ConnectorProtocolEndToEndTest {
    @Autowired ConnectorCredentialService credentials;
    @Autowired SyncControlService control;
    @Autowired SyncRunRepository runs;
    @Autowired SyncAttemptRepository attempts;
    @Autowired ConnectorWorkService workService;
    @Autowired ConnectorCredentialVerifier verifier;
    @Autowired UserAccountRepository users;
    @Autowired AccountingFirmRepository firms;
    @Autowired ClientRepository clients;
    @Autowired CompanyRepository companies;
    @Autowired AccountingSourceRepository sources;
    @Autowired CompanyAccessGrantRepository grants;
    @Autowired AccountingFirmAccessGrantRepository memberships;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @MockitoBean(name="identityClock") Clock clock;
    private final Instant now=Instant.parse("2026-07-01T12:00:00Z");
    private final List<UUID> ownedSources=new ArrayList<>();
    private UUID actor,firmId,clientId,companyId,sourceId,runId;
    private String token;
    @BeforeEach void prepare(){
        when(clock.instant()).thenReturn(now);
        var user=users.saveAndFlush(new UserAccount("Synthetic Protocol Issuer",UUID.randomUUID()+"@example.invalid","hash"));actor=user.getId();
        var firm=firms.saveAndFlush(new AccountingFirm("Synthetic Protocol Firm",true));firmId=firm.getId();
        var client=clients.saveAndFlush(new Client(firm,"Synthetic Protocol Client",true));clientId=client.getId();
        var company=companies.saveAndFlush(new Company(client,"Synthetic Protocol Company","58585858000158"));companyId=company.getId();
        sourceId=sources.saveAndFlush(new AccountingSource(company,AccountingSourceType.QUESTOR,"57",true)).getId();ownedSources.add(sourceId);
        memberships.saveAndFlush(new AccountingFirmAccessGrant(user,firm,Set.of(AccountingFirmPermission.CONNECTOR_MANAGE)));
        grants.saveAndFlush(new CompanyAccessGrant(user,company,CompanyAccessRole.CUSTOM,CostCenterScope.ALL,Set.of(CompanyPermission.QUESTOR_SYNC)));
        token=credentials.issue(actor,firmId,new CreateConnectorCredentialRequest(sourceId,"Synthetic Protocol")).secret();
        runId=control.request(actor,sourceId,UUID.randomUUID(),request()).id();
    }
    @AfterEach void cleanOnlyOwnedSyntheticFixtures(){
        for(var source:ownedSources){
            jdbc.update("DELETE FROM sync_run_events WHERE sync_run_id IN (SELECT id FROM sync_runs WHERE accounting_source_id=?)",source);
            jdbc.update("DELETE FROM sync_runs WHERE accounting_source_id=?",source);
            jdbc.update("DELETE FROM connector_credential_events WHERE credential_id IN (SELECT id FROM connector_credentials WHERE accounting_source_id=?)",source);
            jdbc.update("DELETE FROM connector_credentials WHERE accounting_source_id=?",source);
            jdbc.update("DELETE FROM accounting_movements WHERE accounting_source_id=?",source);
            jdbc.update("DELETE FROM accounting_accounts WHERE accounting_source_id=?",source);
            jdbc.update("DELETE FROM accounting_sources WHERE id=?",source);
        }
        jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id IN (SELECT id FROM company_access_grants WHERE company_id=?)",companyId);
        jdbc.update("DELETE FROM company_access_grants WHERE company_id=?",companyId);
        jdbc.update("DELETE FROM accounting_firm_access_grant_permissions WHERE accounting_firm_access_grant_id IN (SELECT id FROM accounting_firm_access_grants WHERE accounting_firm_id=?)",firmId);
        jdbc.update("DELETE FROM accounting_firm_access_grants WHERE accounting_firm_id=?",firmId);
        jdbc.update("DELETE FROM companies WHERE id=?",companyId);jdbc.update("DELETE FROM clients WHERE id=?",clientId);
        jdbc.update("DELETE FROM accounting_firms WHERE id=?",firmId);jdbc.update("DELETE FROM user_accounts WHERE id=?",actor);
    }
    @Test void shouldRecoverLostPublicationResponseThroughRealReceiptWithoutDuplicatingPostOrMovements() throws Exception {
        var reader=reader();when(reader.readSnapshot(any())).thenAnswer(call->snapshot(call.getArgument(0)));
        try(var bridge=new Bridge();var transport=bridge.transport(()->token)){
            bridge.dropCompletion.set(true);var client=new FinempConnectorClient(transport,sourceId,25000);
            assertThatCode(()->assertThat(client.session().accountingSourceId()).isEqualTo(sourceId)).as("Session contract: %s",bridge.trace).doesNotThrowAnyException();
            try(var cycle=new ConnectorAgentCycle(client,reader,Duration.ofSeconds(10))){assertThat(cycle.runOnce()).as("Wire stages: %s",bridge.trace).isEqualTo(ConnectorAgentCycle.Outcome.SUCCEEDED);}
            assertThat(bridge.completes).hasValue(1);assertThat(bridge.receipts).hasValue(1);assertThat(count(sourceId)).isEqualTo(2);
            var run=runs.findById(runId).orElseThrow();assertThat(run.getStatus()).isEqualTo(SyncRunStatus.SUCCEEDED);
            assertThat(run.getReadCount()).isEqualTo(2);assertThat(run.getImportedCount()).isEqualTo(2);assertThat(run.getSkippedCount()).isZero();
            mvc.perform(post(bridge.completedPath).secure(true).header("Authorization","Bearer "+token).contentType("application/json").content(bridge.completedBody))
                    .andExpect(status().isConflict());
            assertThat(count(sourceId)).isEqualTo(2);assertThat(client.claim()).isEmpty();verify(reader,times(1)).readSnapshot(any());
        }
    }
    @Test void shouldRefuseAnotherSourceCredentialBeforeClaimAndNeverReadOrPublish(){
        var other=sources.saveAndFlush(new AccountingSource(companies.getReferenceById(companyId),AccountingSourceType.QUESTOR,"58",true));ownedSources.add(other.getId());
        var otherToken=credentials.issue(actor,firmId,new CreateConnectorCredentialRequest(other.getId(),"Other Synthetic Protocol")).secret();
        var otherRun=control.request(actor,other.getId(),UUID.randomUUID(),request()).id();var reader=reader();
        try(var bridge=new Bridge();var transport=bridge.transport(()->otherToken);var cycle=new ConnectorAgentCycle(new FinempConnectorClient(transport,sourceId,25000),reader,Duration.ofSeconds(10))){
            assertThat(cycle.runOnce()).as("Wire stages: %s",bridge.trace).isEqualTo(ConnectorAgentCycle.Outcome.RETRY_LATER);
            assertThat(bridge.trace).anyMatch(step->step.contains("/session -> 200"));assertThat(bridge.claims).hasValue(0);assertThat(bridge.completes).hasValue(0);
        }
        verify(reader,never()).readSnapshot(any());assertThat(count(sourceId)).isZero();assertThat(count(other.getId())).isZero();
        assertThat(runs.findById(otherRun).orElseThrow().getStatus()).isEqualTo(SyncRunStatus.REQUESTED);
    }
    @Test void shouldCancelOnlyARequestedRunBeforeClaimAndNeverStartSourceIo(){
        control.cancel(actor,sourceId,runId);var reader=reader();
        try(var bridge=new Bridge();var transport=bridge.transport(()->token);var cycle=new ConnectorAgentCycle(new FinempConnectorClient(transport,sourceId,25000),reader,Duration.ofSeconds(10))){
            assertThat(cycle.runOnce()).as("Wire stages: %s",bridge.trace).isEqualTo(ConnectorAgentCycle.Outcome.IDLE);assertThat(bridge.completes).hasValue(0);assertThat(bridge.receipts).hasValue(0);
        }
        verify(reader,never()).readSnapshot(any());assertThat(count(sourceId)).isZero();assertThat(runs.findById(runId).orElseThrow().getStatus()).isEqualTo(SyncRunStatus.CANCELLED);
    }
    @Test void shouldRejectTheOldBatchAfterLeaseRecoveryCreatesANewFence(){
        var reader=reader();when(reader.readSnapshot(any())).thenAnswer(call->{
            var original=attempts.findFirstBySyncRunIdOrderByAttemptNumberDesc(runId).orElseThrow();
            when(clock.instant()).thenReturn(original.getLeaseExpiresAt().plusSeconds(1));
            var identity=verifier.authenticate(token).orElseThrow();assertThat(workService.claim(identity)).isEmpty();
            var expired=attempts.findById(original.getId()).orElseThrow();assertThat(expired.getNextAttemptAt()).isNotNull();
            when(clock.instant()).thenReturn(expired.getNextAttemptAt().plusSeconds(1));
            var replacement=workService.claim(identity).orElseThrow();assertThat(replacement.attemptNumber()).isEqualTo(2);
            return snapshot(call.getArgument(0));
        });
        try(var bridge=new Bridge();var transport=bridge.transport(()->token);var cycle=new ConnectorAgentCycle(new FinempConnectorClient(transport,sourceId,25000),reader,Duration.ofSeconds(10))){
            assertThat(cycle.runOnce()).as("Wire stages: %s",bridge.trace).isEqualTo(ConnectorAgentCycle.Outcome.FAILED);
            assertThat(bridge.completes).hasValue(1);assertThat(bridge.receipts).hasValue(1);
        }
        assertThat(count(sourceId)).isZero();var current=runs.findById(runId).orElseThrow();assertThat(current.getAttemptNumber()).isEqualTo(2);assertThat(current.getStatus()).isEqualTo(SyncRunStatus.RUNNING);
    }
    @Test void shouldRefuseOutOfPeriodRowsBeforeSendingAnyBatch(){
        var reader=reader();when(reader.readSnapshot(any())).thenReturn(new AccountingSourceSnapshot(new ExternalCompanyIdentity("57","58585858000158"),
                List.of(new ImportedAccountingMovement("synthetic",LocalDate.of(2026,8,1),"100","1.100","Synthetic",AccountingMovementSide.DEBIT,BigDecimal.ONE,null))));
        try(var bridge=new Bridge();var transport=bridge.transport(()->token);var cycle=new ConnectorAgentCycle(new FinempConnectorClient(transport,sourceId,25000),reader,Duration.ofSeconds(10))){
            assertThat(cycle.runOnce()).as("Wire stages: %s",bridge.trace).isEqualTo(ConnectorAgentCycle.Outcome.PENDING);assertThat(bridge.completes).hasValue(0);
        }
        assertThat(count(sourceId)).isZero();
    }
    private AccountingSourceSnapshotReader reader(){var reader=mock(AccountingSourceSnapshotReader.class);when(reader.sourceType()).thenReturn(AccountingSourceType.QUESTOR);return reader;}
    private AccountingSourceSnapshot snapshot(AccountingReadRequest request){
        assertThat(request.accountingSourceId()).isEqualTo(sourceId);assertThat(request.externalCompanyReference()).isEqualTo("57");
        return new AccountingSourceSnapshot(new ExternalCompanyIdentity("57","58585858000158"),List.of(
                new ImportedAccountingMovement("synthetic-journal",LocalDate.of(2026,7,15),"100","1.100","Synthetic Debit",AccountingMovementSide.DEBIT,new BigDecimal("1.2300"),null),
                new ImportedAccountingMovement("synthetic-journal",LocalDate.of(2026,7,15),"200","2.200","Synthetic Credit",AccountingMovementSide.CREDIT,new BigDecimal("-1.2300"),null)));
    }
    private long count(UUID source){return jdbc.queryForObject("SELECT COUNT(*) FROM accounting_movements WHERE accounting_source_id=?",Long.class,source);}
    private RequestSyncRun request(){return new RequestSyncRun(SyncRunMode.INITIAL,LocalDate.of(2026,7,1),LocalDate.of(2026,7,31));}
    private final class Bridge implements AutoCloseable {
        private final ExecutorService executor=Executors.newFixedThreadPool(2);
        private final AtomicBoolean dropCompletion=new AtomicBoolean();
        private final AtomicInteger completes=new AtomicInteger(),receipts=new AtomicInteger(),claims=new AtomicInteger();
        private final List<String> trace=new CopyOnWriteArrayList<>();
        private volatile String completedPath;
        private volatile byte[] completedBody;
        @SuppressWarnings({"unchecked","rawtypes"})
        ConnectorHttpsTransport transport(java.util.function.Supplier<String> secret){
            var http=mock(HttpClient.class);when(http.followRedirects()).thenReturn(HttpClient.Redirect.NEVER);
            var ssl=new SSLParameters();ssl.setEndpointIdentificationAlgorithm("HTTPS");when(http.sslParameters()).thenReturn(ssl);
            when(http.sendAsync(any(),any(HttpResponse.BodyHandler.class))).thenAnswer(call->{
                HttpRequest request=call.getArgument(0);HttpResponse.BodyHandler<byte[]> handler=call.getArgument(1);
                return CompletableFuture.supplyAsync(()->dispatch(request,handler),executor);
            });
            return new ConnectorHttpsTransport(URI.create("https://connector.invalid"),secret,Duration.ofSeconds(20),http);
        }
        @SuppressWarnings({"unchecked","rawtypes"})
        private HttpResponse<byte[]> dispatch(HttpRequest request,HttpResponse.BodyHandler<byte[]> handler){
            try{
                String path=request.uri().getPath();byte[] body=body(request);
                if(path.endsWith("/claim"))claims.incrementAndGet();if(path.endsWith("/result"))receipts.incrementAndGet();
                if(path.endsWith("/complete")){completes.incrementAndGet();completedPath=path;completedBody=body;}
                var builder=org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(org.springframework.http.HttpMethod.valueOf(request.method()),path).secure(true).content(body);
                request.headers().map().forEach((name,values)->builder.header(name,values.toArray()));
                var result=mvc.perform(builder).andReturn();
                trace.add(request.method()+" "+path+" -> "+result.getResponse().getStatus()+" type="+result.getResponse().getContentType()+" session="+(result.getRequest().getSession(false)!=null));
                assertThat(result.getRequest().getSession(false)).isNull();
                if(path.endsWith("/complete")&&dropCompletion.compareAndSet(true,false))throw new IOException("Synthetic lost response after commit");
                var response=result.getResponse();var values=new HashMap<String,List<String>>();
                for(var name:response.getHeaderNames())values.put(name,new ArrayList<>(response.getHeaders(name)));
                var headers=HttpHeaders.of(values,(a,b)->true);var info=mock(HttpResponse.ResponseInfo.class);
                when(info.statusCode()).thenReturn(response.getStatus());when(info.headers()).thenReturn(headers);
                var subscriber=handler.apply(info);subscriber.onSubscribe(new Flow.Subscription(){public void request(long count){}public void cancel(){}});
                subscriber.onNext(List.of(ByteBuffer.wrap(response.getContentAsByteArray())));subscriber.onComplete();
                var reply=mock(HttpResponse.class);when(reply.statusCode()).thenReturn(response.getStatus());when(reply.headers()).thenReturn(headers);
                when(reply.body()).thenReturn(subscriber.getBody().toCompletableFuture().get(5,TimeUnit.SECONDS));return reply;
            }catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new CompletionException(interrupted);}
            catch(Exception failure){trace.add("Bridge failure: "+failure.getClass().getSimpleName());throw new CompletionException(failure);}
        }
        private byte[] body(HttpRequest request)throws Exception{
            if(request.bodyPublisher().isEmpty())return new byte[0];var bytes=new ByteArrayOutputStream();var complete=new CompletableFuture<byte[]>();
            request.bodyPublisher().get().subscribe(new Flow.Subscriber<ByteBuffer>(){
                Flow.Subscription subscription;
                public void onSubscribe(Flow.Subscription value){subscription=value;value.request(1);}
                public void onNext(ByteBuffer buffer){if(buffer.remaining()>8*1024*1024-bytes.size()){subscription.cancel();complete.completeExceptionally(new IllegalStateException("Synthetic bridge budget"));return;}
                    while(buffer.hasRemaining())bytes.write(buffer.get());subscription.request(1);}
                public void onError(Throwable failure){complete.completeExceptionally(failure);}
                public void onComplete(){complete.complete(bytes.toByteArray());}
            });
            return complete.get(5,TimeUnit.SECONDS);
        }
        @Override public void close(){executor.shutdownNow();}
    }
}
