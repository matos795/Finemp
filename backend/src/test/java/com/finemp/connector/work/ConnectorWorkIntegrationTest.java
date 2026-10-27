package com.finemp.connector.work;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.finemp.TestcontainersConfiguration;
import com.finemp.access.*;
import com.finemp.accounting.source.*;
import com.finemp.accounting.sync.*;
import com.finemp.accounting.sync.control.*;
import com.finemp.accounting.sync.execution.*;
import com.finemp.accountingfirm.*;
import com.finemp.client.*;
import com.finemp.company.*;
import com.finemp.connector.*;
import com.finemp.connector.completion.*;
import com.finemp.accounting.sync.publication.AccountingPublicationTransaction;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {"finemp.connector.enabled=true", "finemp.connector.protocol.enabled=true", "finemp.sync.control.enabled=true",
        "finemp.sync.publication.max-records=2"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ConnectorWorkIntegrationTest {
    @Autowired private ConnectorWorkService work;
    @Autowired private ConnectorCredentialService credentialService;
    @Autowired private ConnectorCredentialVerifier verifier;
    @Autowired private SyncControlService control;
    @Autowired private SyncExecutionLifecycle lifecycle;
    @Autowired private SyncRunRepository runs;
    @Autowired private SyncAttemptRepository attempts;
    @Autowired private UserAccountRepository users;
    @Autowired private AccountingFirmRepository firms;
    @Autowired private ClientRepository clients;
    @Autowired private CompanyRepository companies;
    @Autowired private AccountingSourceRepository sources;
    @Autowired private CompanyAccessGrantRepository grants;
    @Autowired private AccountingFirmAccessGrantRepository memberships;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;
    @Autowired private ApplicationContext context;
    @Autowired private ConnectorCompletionService completion;
    @Autowired private ConnectorCompletionContext receipts;
    @Autowired private ConnectorBatchDecoder decoder;
    @Autowired private AccountingPublicationTransaction publication;
    @Autowired private org.springframework.transaction.PlatformTransactionManager transactionManager;
    @MockitoBean(name = "identityClock") private Clock clock;
    private final Instant now = Instant.parse("2026-07-01T12:00:00Z");
    private final List<UUID> ownSources = new ArrayList<>(), ownUsers = new ArrayList<>();
    private UUID actor, firmId, clientId, companyId, sourceId, runId;
    private ConnectorSecretResponse issued;
    private ConnectorIdentity identity;
    @BeforeEach void prepare() {
        at(0);
        var user = users.saveAndFlush(new UserAccount("Synthetic Work Issuer", UUID.randomUUID() + "@example.invalid", "hash"));
        actor = user.getId(); ownUsers.add(actor);
        var firm = firms.saveAndFlush(new AccountingFirm("Synthetic Work Firm", true)); firmId = firm.getId();
        var client = clients.saveAndFlush(new Client(firm, "Synthetic Work Client", true)); clientId = client.getId();
        var company = companies.saveAndFlush(new Company(client, "Synthetic Work Company", "47474747000147")); companyId = company.getId();
        sourceId = sources.saveAndFlush(new AccountingSource(company, AccountingSourceType.QUESTOR, "synthetic", true)).getId(); ownSources.add(sourceId);
        memberships.saveAndFlush(new AccountingFirmAccessGrant(user, firm, Set.of(AccountingFirmPermission.CONNECTOR_MANAGE)));
        grants.saveAndFlush(new CompanyAccessGrant(user, company, CompanyAccessRole.CUSTOM, CostCenterScope.ALL, Set.of(CompanyPermission.QUESTOR_SYNC)));
        issued = credentialService.issue(actor, firmId, new CreateConnectorCredentialRequest(sourceId, "Synthetic Work"));
        identity = verifier.authenticate(issued.secret()).orElseThrow();
        runId = control.request(actor, sourceId, UUID.randomUUID(), request()).id();
    }
    @AfterEach void cleanOnlyOwnSyntheticFixtures() {
        for (var source : ownSources) {
            jdbc.update("DELETE FROM sync_run_events WHERE sync_run_id IN (SELECT id FROM sync_runs WHERE accounting_source_id = ?)", source);
            jdbc.update("DELETE FROM sync_runs WHERE accounting_source_id = ?", source);
            jdbc.update("DELETE FROM connector_credential_events WHERE credential_id IN (SELECT id FROM connector_credentials WHERE accounting_source_id = ?)", source);
            jdbc.update("DELETE FROM connector_credentials WHERE accounting_source_id = ?", source);
            jdbc.update("DELETE FROM accounting_movements WHERE accounting_source_id = ?", source);
            jdbc.update("DELETE FROM accounting_accounts WHERE accounting_source_id = ?", source);
            jdbc.update("DELETE FROM accounting_sources WHERE id = ?", source);
        }
        jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id IN (SELECT id FROM company_access_grants WHERE company_id = ?)", companyId);
        jdbc.update("DELETE FROM company_access_grants WHERE company_id = ?", companyId);
        jdbc.update("DELETE FROM accounting_firm_access_grant_permissions WHERE accounting_firm_access_grant_id IN (SELECT id FROM accounting_firm_access_grants WHERE accounting_firm_id = ?)", firmId);
        jdbc.update("DELETE FROM accounting_firm_access_grants WHERE accounting_firm_id = ?", firmId);
        jdbc.update("DELETE FROM companies WHERE id = ?", companyId); jdbc.update("DELETE FROM clients WHERE id = ?", clientId);
        jdbc.update("DELETE FROM accounting_firms WHERE id = ?", firmId);
        for (var user : ownUsers) jdbc.update("DELETE FROM user_accounts WHERE id = ?", user);
    }
    @Test void shouldClaimBoundContextWithoutInstallingAnErpReaderOrDirectExecutorOnTheSaasServer() {
        assertThat(context.getBeansOfType(SyncRunExecutor.class)).isEmpty();
        assertThat(context.getBeansOfType(com.finemp.accounting.reading.AccountingCompanyIdentityReader.class)).isEmpty();
        var result = work.claim(identity).orElseThrow();
        assertThat(result.context().accountingSourceId()).isEqualTo(sourceId);
        assertThat(result.context().expectedCompanyCnpj()).isEqualTo("47474747000147");
        assertThat(result.context().startDate()).isEqualTo(LocalDate.of(2026,7,1));
        assertThat(attempts.findById(result.attemptId()).orElseThrow().getAgent())
                .isEqualTo(new SyncClaimAgent(identity.credentialId(), identity.version()));
        assertThat(work.claim(identity)).isEmpty();
        assertThat(lifecycle.claim(runId)).isEmpty();
    }
    @Test void shouldNeverMixSourceBindingsEvenWhenBothBelongToTheSameCompanyAndIssuer() {
        var source = sources.saveAndFlush(new AccountingSource(companies.getReferenceById(companyId), AccountingSourceType.QUESTOR, "synthetic-other", true));
        ownSources.add(source.getId());
        var otherSecret = credentialService.issue(actor, firmId, new CreateConnectorCredentialRequest(source.getId(), "Other Synthetic Work"));
        var otherIdentity = verifier.authenticate(otherSecret.secret()).orElseThrow();
        control.request(actor, source.getId(), UUID.randomUUID(), request());
        var first = work.claim(identity).orElseThrow(); var second = work.claim(otherIdentity).orElseThrow();
        assertThat(first.context().accountingSourceId()).isEqualTo(sourceId);
        assertThat(second.context().accountingSourceId()).isEqualTo(source.getId());
        assertThatThrownBy(() -> work.renew(identity, second.attemptId())).isInstanceOf(com.finemp.administration.SaasResourceNotFoundException.class);
    }
    @Test void shouldRenewOnlyTheCurrentLiveLeaseAndLeaveTheFenceUnchanged() {
        var result = work.claim(identity).orElseThrow(); var before = run().getVersion();
        at(200); var renewed = work.renew(identity, result.attemptId());
        assertThat(renewed.leaseExpiresAt()).isEqualTo(now.plusSeconds(500)); assertThat(run().getVersion()).isEqualTo(before);
        at(500); assertThatThrownBy(() -> work.renew(identity, result.attemptId())).isInstanceOf(ConnectorWorkNotCurrentException.class);
    }
    @Test void shouldRecoverAnExpiredAttemptOnlyAfterBackoffWithANewFence() {
        var first = work.claim(identity).orElseThrow(); var version = run().getVersion();
        at(300); assertThat(work.claim(identity)).isEmpty();
        assertThat(attempts.findById(first.attemptId()).orElseThrow().getStatus()).isEqualTo(SyncAttemptStatus.EXPIRED);
        at(329); assertThat(work.claim(identity)).isEmpty();
        at(330); var next = work.claim(identity).orElseThrow();
        assertThat(next.attemptNumber()).isEqualTo(2); assertThat(run().getVersion()).isGreaterThan(version);
        assertThatThrownBy(() -> work.renew(identity, first.attemptId())).isInstanceOf(ConnectorWorkNotCurrentException.class);
    }
    @Test void shouldInvalidateRotatedCredentialSnapshotsAndBindTheRetryToTheNewVersion() {
        var first = work.claim(identity).orElseThrow();
        var rotated = credentialService.rotate(actor, firmId, identity.credentialId());
        assertThatThrownBy(() -> work.renew(identity, first.attemptId())).isInstanceOf(ConnectorCredentialUnavailableException.class);
        var current = verifier.authenticate(rotated.secret()).orElseThrow();
        assertThatThrownBy(() -> work.renew(current, first.attemptId())).isInstanceOf(com.finemp.administration.SaasResourceNotFoundException.class);
        at(300); assertThat(work.claim(current)).isEmpty(); at(330);
        var next = work.claim(current).orElseThrow();
        assertThat(attempts.findById(next.attemptId()).orElseThrow().getAgent().credentialVersion()).isEqualTo(current.version());
    }
    @Test void shouldRecheckTheHumanRequesterIndependentlyOfTheCredentialIssuer() {
        control.cancel(actor, sourceId, runId);
        var requester = users.saveAndFlush(new UserAccount("Synthetic Work Requester", UUID.randomUUID() + "@example.invalid", "hash")); ownUsers.add(requester.getId());
        var grant = grants.saveAndFlush(new CompanyAccessGrant(requester, companies.getReferenceById(companyId), CompanyAccessRole.CUSTOM,
                CostCenterScope.ALL, Set.of(CompanyPermission.QUESTOR_SYNC)));
        runId = control.request(requester.getId(), sourceId, UUID.randomUUID(), request()).id();
        jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id = ?", grant.getId());
        assertThat(verifier.isCurrent(identity)).isTrue();
        assertThat(work.claim(identity)).isEmpty(); assertThat(run().getFailureCode()).isEqualTo(SyncFailureCode.ACCESS_REVOKED);
    }
    @Test void shouldGrantOneAttemptAcrossConcurrentPolls() throws Exception {
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> claim = () -> { start.await(10, TimeUnit.SECONDS); return work.claim(identity).isPresent(); };
            var first = pool.submit(claim); var second = pool.submit(claim); start.countDown();
            assertThat(first.get(15, TimeUnit.SECONDS)).isNotEqualTo(second.get(15, TimeUnit.SECONDS));
        }
        assertThat(run().getAttemptNumber()).isEqualTo(1);
    }
    @Test void shouldExposeClaimAndHeartbeatWithTlsBearerAndIgnoreClientSuppliedScopeOrDates() throws Exception {
        mvc.perform(post("/api/connector/v1/work/claim").secure(true).header("Authorization", "Bearer " + issued.secret())
                .contentType("application/json").content("{\"accountingSourceId\":\"" + UUID.randomUUID() + "\",\"startDate\":\"1900-01-01\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.context.accountingSourceId").value(sourceId.toString()))
                .andExpect(jsonPath("$.context.startDate").value("2026-07-01"));
        var attempt = attempts.findFirstBySyncRunIdOrderByAttemptNumberDesc(runId).orElseThrow();
        at(200);
        mvc.perform(post("/api/connector/v1/work/" + attempt.getId() + "/heartbeat").secure(true)
                .header("Authorization", "Bearer " + issued.secret())).andExpect(status().isOk())
                .andExpect(jsonPath("$.leaseExpiresAt").value("2026-07-01T12:08:20Z"));
        mvc.perform(post("/api/connector/v1/work/" + UUID.randomUUID() + "/heartbeat").secure(true)
                .header("Authorization", "Bearer " + issued.secret())).andExpect(status().isNotFound());
        mvc.perform(post("/api/connector/v1/work/not-a-uuid/heartbeat").secure(true)
                .header("Authorization", "Bearer " + issued.secret())).andExpect(status().isBadRequest());
    }
    @Test void shouldPublishAtomicCountersOfferReceiptAndKeepReplayIdempotent() {
        var first = work.claim(identity).orElseThrow();
        var published = completion.complete(identity, first.attemptId(), input(batch("10.0000")));
        assertThat(published.httpStatus()).isEqualTo(200);
        assertThat(published.result().attemptStatus()).isEqualTo(SyncAttemptStatus.SUCCEEDED);
        assertThat(published.result().readCount()).isEqualTo(2); assertThat(published.result().importedCount()).isEqualTo(2);
        assertThat(receipts.result(identity, first.attemptId())).isEqualTo(published.result());
        assertThatThrownBy(() -> completion.complete(identity, first.attemptId(), input(batch("10.0000"))))
                .isInstanceOf(ConnectorWorkNotCurrentException.class);
        nextRun(); var replay = work.claim(identity).orElseThrow();
        var repeated = completion.complete(identity, replay.attemptId(), input(batch("10.0000")));
        assertThat(repeated.result().importedCount()).isZero(); assertThat(repeated.result().skippedCount()).isEqualTo(2);
        assertThat(movementCount(sourceId)).isEqualTo(2);
    }
    @Test void shouldRejectObservedIdentityOutOfWindowAndUnrepresentableMoneyWithoutPublishingAnyRow() {
        var cases = List.of(
                Map.entry(batch("10").replace("47.474.747/0001-47", "48484848000148"), SyncFailureCode.IDENTITY_MISMATCH),
                Map.entry(batch("10").replace("2026-07-01", "2026-08-01"), SyncFailureCode.INVALID_PAYLOAD),
                Map.entry(batch("1e600000000"), SyncFailureCode.INVALID_PAYLOAD));
        for (int index = 0; index < cases.size(); index++) {
            var candidate = cases.get(index); var lease = work.claim(identity).orElseThrow();
            var outcome = completion.complete(identity, lease.attemptId(), input(candidate.getKey()));
            assertThat(outcome.httpStatus()).isEqualTo(422); assertThat(outcome.result().attemptFailureCode()).isEqualTo(candidate.getValue());
            assertThat(outcome.result().readCount()).isNull(); assertThat(movementCount(sourceId)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM accounting_accounts WHERE accounting_source_id = ?", Long.class, sourceId)).isZero();
            if (index + 1 < cases.size()) nextRun();
        }
    }
    @Test void shouldPreservePriorDataAndReturnSpecificConflictWhenAnExistingMovementChanges() {
        var first = work.claim(identity).orElseThrow(); completion.complete(identity, first.attemptId(), input(batch("10")));
        nextRun(); var changed = work.claim(identity).orElseThrow();
        var result = completion.complete(identity, changed.attemptId(), input(batch("11")));
        assertThat(result.httpStatus()).isEqualTo(422); assertThat(result.result().attemptFailureCode()).isEqualTo(SyncFailureCode.DATA_CONFLICT);
        assertThat(movementCount(sourceId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT amount FROM accounting_movements WHERE accounting_source_id = ? AND side = 'DEBIT'",
                java.math.BigDecimal.class, sourceId)).isEqualByComparingTo("10.0000");
    }
    @Test void shouldRollbackPublicationAndSuccessBeforeSchedulingRetryIfTerminalAuditFails() {
        var first = work.claim(identity).orElseThrow();
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String function = "f_connector_pub_" + suffix, trigger = "t_connector_pub_" + suffix;
        jdbc.execute("CREATE FUNCTION " + function + "() RETURNS TRIGGER LANGUAGE plpgsql AS $$ BEGIN IF NEW.sync_run_id = '"
                + runId + "'::uuid AND NEW.status = 'SUCCEEDED' THEN RAISE EXCEPTION 'Synthetic connector publication audit failure'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER " + trigger + " BEFORE INSERT ON sync_run_events FOR EACH ROW EXECUTE FUNCTION " + function + "()");
        try {
            var failed = completion.complete(identity, first.attemptId(), input(batch("10")));
            assertThat(failed.httpStatus()).isEqualTo(202); assertThat(failed.result().attemptFailureCode()).isEqualTo(SyncFailureCode.PUBLICATION_FAILED);
            assertThat(failed.result().runStatus()).isEqualTo(SyncRunStatus.RUNNING); assertThat(failed.result().readCount()).isNull();
            assertThat(movementCount(sourceId)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM accounting_accounts WHERE accounting_source_id = ?", Long.class, sourceId)).isZero();
        } finally {
            jdbc.execute("DROP TRIGGER " + trigger + " ON sync_run_events"); jdbc.execute("DROP FUNCTION " + function + "()");
        }
        at(30); var retry = work.claim(identity).orElseThrow(); completion.complete(identity, retry.attemptId(), input(batch("10")));
        var olderReceipt = receipts.result(identity, first.attemptId());
        assertThat(olderReceipt.runStatus()).isEqualTo(SyncRunStatus.SUCCEEDED); assertThat(olderReceipt.currentAttempt()).isFalse();
        assertThat(olderReceipt.attemptStatus()).isEqualTo(SyncAttemptStatus.FAILED); assertThat(olderReceipt.importedCount()).isNull();
    }
    @Test void shouldRevalidateMachineVersionInsidePublicationAfterTheContextWasResolved() {
        var workItem = work.claim(identity).orElseThrow();
        var claim = receipts.currentClaim(identity, workItem.attemptId());
        var parsed = decoder.decode(input(batch("10")));
        credentialService.rotate(actor, firmId, identity.credentialId());
        assertThatThrownBy(() -> publication.publishConnector(claim, parsed.observedIdentity(), parsed.movements()))
                .isInstanceOf(StaleSyncClaimException.class);
        assertThat(movementCount(sourceId)).isZero();
    }
    @Test void shouldKeepCompletionAndReceiptsInTheExactCredentialSourceScope() {
        var other = sources.saveAndFlush(new AccountingSource(companies.getReferenceById(companyId), AccountingSourceType.QUESTOR, "synthetic-other", true));
        ownSources.add(other.getId());
        var key = credentialService.issue(actor, firmId, new CreateConnectorCredentialRequest(other.getId(), "Other Synthetic Completion"));
        var otherIdentity = verifier.authenticate(key.secret()).orElseThrow();
        control.request(actor, other.getId(), UUID.randomUUID(), request());
        var lease = work.claim(otherIdentity).orElseThrow();
        assertThatThrownBy(() -> completion.complete(identity, lease.attemptId(), input(batch("10"))))
                .isInstanceOf(com.finemp.administration.SaasResourceNotFoundException.class);
        assertThatThrownBy(() -> receipts.result(identity, lease.attemptId()))
                .isInstanceOf(com.finemp.administration.SaasResourceNotFoundException.class);
        String payload = batch("10").replace("\"externalReference\":\"synthetic\"", "\"externalReference\":\"synthetic-other\"");
        assertThat(completion.complete(otherIdentity, lease.attemptId(), input(payload)).httpStatus()).isEqualTo(200);
        assertThat(movementCount(other.getId())).isEqualTo(2); assertThat(movementCount(sourceId)).isZero();
    }
    @Test void shouldRejectCompletionAfterLeaseExpiryWithoutChangingCountersOrRows() {
        var lease = work.claim(identity).orElseThrow(); at(300);
        assertThatThrownBy(() -> completion.complete(identity, lease.attemptId(), input(batch("10"))))
                .isInstanceOf(ConnectorWorkNotCurrentException.class);
        assertThat(movementCount(sourceId)).isZero(); assertThat(run().getReadCount()).isNull();
    }
    @Test void shouldRevalidateTheHumanRequesterAgainWhenTheBatchIsPublished() {
        control.cancel(actor, sourceId, runId);
        var requester = users.saveAndFlush(new UserAccount("Synthetic Completion Requester", UUID.randomUUID() + "@example.invalid", "hash"));
        ownUsers.add(requester.getId());
        var grant = grants.saveAndFlush(new CompanyAccessGrant(requester, companies.getReferenceById(companyId), CompanyAccessRole.CUSTOM,
                CostCenterScope.ALL, Set.of(CompanyPermission.QUESTOR_SYNC)));
        runId = control.request(requester.getId(), sourceId, UUID.randomUUID(), request()).id();
        var lease = work.claim(identity).orElseThrow();
        jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id = ?", grant.getId());
        assertThat(verifier.isCurrent(identity)).isTrue();
        var result = completion.complete(identity, lease.attemptId(), input(batch("10")));
        assertThat(result.httpStatus()).isEqualTo(422); assertThat(result.result().runFailureCode()).isEqualTo(SyncFailureCode.ACCESS_REVOKED);
        assertThat(movementCount(sourceId)).isZero();
    }
    @Test void shouldKeepSourceFailureReportsRestrictedAndRefuseOuterTransactionsBeforeParsing() {
        var lease = work.claim(identity).orElseThrow();
        var tx = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> tx.execute(status -> completion.complete(identity, lease.attemptId(), input(batch("10")))))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        assertThatThrownBy(() -> completion.reportFailure(identity, lease.attemptId(), input("{\"code\":\"READ_FAILED\",\"message\":\"synthetic detail\"}")))
                .isInstanceOf(InvalidConnectorBatchException.class);
        assertThat(run().getStatus()).isEqualTo(SyncRunStatus.RUNNING);
        var result = completion.reportFailure(identity, lease.attemptId(), input("{\"code\":\"RESOURCE_LIMIT\"}"));
        assertThat(result.httpStatus()).isEqualTo(422); assertThat(result.result().runFailureCode()).isEqualTo(SyncFailureCode.RESOURCE_LIMIT);
        assertThat(movementCount(sourceId)).isZero();
    }
    @Test void shouldExposeReceiptAndNeverMutateRunForMalformedOrOverBudgetJson() throws Exception {
        var lease = work.claim(identity).orElseThrow(); String route = "/api/connector/v1/work/" + lease.attemptId();
        mvc.perform(post(route + "/complete").secure(true).header("Authorization", "Bearer " + issued.secret())
                .contentType("application/json").content(batch("10") + "{}")).andExpect(status().isBadRequest());
        String third = "{\"sourceEntryKey\":\"third\",\"accountingDate\":\"2026-07-01\",\"externalAccountId\":\"300\",\"accountKey\":\"3.300\",\"accountDescription\":\"Synthetic\",\"side\":\"DEBIT\",\"amount\":1}";
        mvc.perform(post(route + "/complete").secure(true).header("Authorization", "Bearer " + issued.secret())
                .contentType("application/json").content(batch("10").replace("\"movements\":[", "\"movements\":[" + third + ",")))
                .andExpect(status().isPayloadTooLarge());
        assertThat(run().getStatus()).isEqualTo(SyncRunStatus.RUNNING); assertThat(movementCount(sourceId)).isZero();
        mvc.perform(post(route + "/complete").secure(true).header("Authorization", "Bearer " + issued.secret())
                .contentType("application/json").content(batch("10"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.attemptStatus").value("SUCCEEDED")).andExpect(jsonPath("$.importedCount").value(2));
        mvc.perform(get(route + "/result").secure(true).header("Authorization", "Bearer " + issued.secret()))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.readCount").value(2));
        mvc.perform(post(route + "/complete").secure(true).header("Authorization", "Bearer " + issued.secret())
                .contentType("application/json").content(batch("10"))).andExpect(status().isConflict());
    }
    private ByteArrayInputStream input(String json) { return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)); }
    private long movementCount(UUID source) { return jdbc.queryForObject("SELECT COUNT(*) FROM accounting_movements WHERE accounting_source_id = ?", Long.class, source); }
    private void nextRun() { runId = control.request(actor, sourceId, UUID.randomUUID(), request()).id(); }
    private String batch(String debitAmount) {
        return """
                {"observedIdentity":{"externalReference":"synthetic","cnpj":"47.474.747/0001-47"},"movements":[
                {"sourceEntryKey":"journal","accountingDate":"2026-07-01","externalAccountId":"100","accountKey":"1.100","accountDescription":"Synthetic Debit","side":"DEBIT","amount":%s,"history":"Synthetic"},
                {"sourceEntryKey":"journal","accountingDate":"2026-07-01","externalAccountId":"200","accountKey":"2.200","accountDescription":"Synthetic Credit","side":"CREDIT","amount":-10.0000,"history":"Synthetic"}
                ]}
                """.formatted(debitAmount);
    }
    private void at(long seconds) { when(clock.instant()).thenReturn(now.plusSeconds(seconds)); }
    private RequestSyncRun request() { return new RequestSyncRun(SyncRunMode.INITIAL, LocalDate.of(2026,7,1), LocalDate.of(2026,7,31)); }
    private SyncRun run() { return runs.findByIdAndAccountingSourceId(runId, sourceId).orElseThrow(); }
}
