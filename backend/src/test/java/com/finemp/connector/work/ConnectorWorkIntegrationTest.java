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

@SpringBootTest(properties = {"finemp.connector.enabled=true", "finemp.connector.protocol.enabled=true", "finemp.sync.control.enabled=true"})
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
    private void at(long seconds) { when(clock.instant()).thenReturn(now.plusSeconds(seconds)); }
    private RequestSyncRun request() { return new RequestSyncRun(SyncRunMode.INITIAL, LocalDate.of(2026,7,1), LocalDate.of(2026,7,31)); }
    private SyncRun run() { return runs.findByIdAndAccountingSourceId(runId, sourceId).orElseThrow(); }
}
