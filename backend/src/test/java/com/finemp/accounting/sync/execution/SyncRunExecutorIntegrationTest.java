package com.finemp.accounting.sync.execution;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.finemp.TestcontainersConfiguration;
import com.finemp.access.*;
import com.finemp.accounting.reading.*;
import com.finemp.accounting.source.*;
import com.finemp.accounting.sync.*;
import com.finemp.accounting.sync.control.*;
import com.finemp.company.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;

@SpringBootTest(properties = {"finemp.sync.control.enabled=true", "finemp.sync.executor.enabled=true", "finemp.sync.retry.max-attempts=1"})
@Import(TestcontainersConfiguration.class)
class SyncRunExecutorIntegrationTest {
    @Autowired private SyncRunExecutor executor;
    @Autowired private SyncExecutionLifecycle lifecycle;
    @Autowired private SyncControlService control;
    @Autowired private SyncRunRepository runs;
    @Autowired private SyncRunEventRepository events;
    @Autowired private SyncAttemptRepository attempts;
    @Autowired private UserAccountRepository users;
    @Autowired private CompanyRepository companies;
    @Autowired private AccountingSourceRepository sources;
    @Autowired private CompanyAccessGrantRepository grants;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoBean private AccountingCompanyIdentityReader identities;
    @MockitoBean private AccountingSourceReaderRegistry readers;
    @MockitoBean private SyncPublicationPort publication;
    @MockitoBean(name = "identityClock") private Clock clock;
    private final Instant now = Instant.parse("2026-07-01T12:00:00Z");
    private UUID actor, companyId, sourceId, runId, grantId;

    @BeforeEach void prepare() {
        when(clock.instant()).thenReturn(now);
        var user = users.saveAndFlush(new UserAccount("Synthetic Executor", UUID.randomUUID() + "@example.invalid", "hash"));
        var company = companies.saveAndFlush(new Company("Synthetic Executor", "28282828000128"));
        var source = sources.saveAndFlush(new AccountingSource(company, AccountingSourceType.QUESTOR, "synthetic", true));
        var grant = grants.saveAndFlush(new CompanyAccessGrant(user, company, CompanyAccessRole.CUSTOM, CostCenterScope.ALL, Set.of(CompanyPermission.QUESTOR_SYNC)));
        actor = user.getId(); companyId = company.getId(); sourceId = source.getId(); grantId = grant.getId();
        runId = control.request(actor, sourceId, UUID.randomUUID(), request(SyncRunMode.INITIAL)).id();
        when(identities.readIdentity(any())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return new ExternalCompanyIdentity("synthetic", "28.282.828/0001-28");
        });
        when(readers.readMovements(any())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return List.of();
        });
        // Explicit fake for this decomposition test: fulfills the publication contract for an empty batch only.
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            ClaimedSyncRun claim = call.getArgument(0);
            assertThat((List<?>) call.getArgument(1)).isEmpty();
            var tx = new TransactionTemplate(transactionManager);
            tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
            tx.executeWithoutResult(status -> {
                sources.findLockedForSyncById(sourceId).orElseThrow();
                var run = runs.findByIdAndAccountingSourceId(claim.runId(), sourceId).orElseThrow();
                assertThat(run.getVersion()).isEqualTo(claim.version());
                attempts.findBySyncRunIdAndFenceVersion(runId, claim.version()).orElseThrow().succeed(now);
                run.succeed(0, 0, 0, now);
                events.save(new SyncRunEvent(run, SyncRunActorType.SYSTEM, null, now));
            });
            return null;
        }).when(publication).publish(any(), anyList());
    }
    @AfterEach void cleanOnlyOwnSyntheticRows() {
        jdbc.update("DELETE FROM sync_run_events WHERE sync_run_id IN (SELECT id FROM sync_runs WHERE accounting_source_id = ?)", sourceId);
        jdbc.update("DELETE FROM sync_runs WHERE accounting_source_id = ?", sourceId);
        jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id = ?", grantId);
        jdbc.update("DELETE FROM company_access_grants WHERE id = ?", grantId);
        jdbc.update("DELETE FROM accounting_sources WHERE id = ?", sourceId);
        jdbc.update("DELETE FROM companies WHERE id = ?", companyId);
        jdbc.update("DELETE FROM user_accounts WHERE id = ?", actor);
    }
    @Test void shouldReadOutsideTransactionAndDelegateAtomicEmptyPublicationWithCausalHistory() {
        assertThat(executor.execute(runId)).isEqualTo(SyncExecutionOutcome.PUBLISHED);
        assertThat(run().getStatus()).isEqualTo(SyncRunStatus.SUCCEEDED);
        assertThat(run().getReadCount()).isZero();
        assertThat(control.events(actor, sourceId, runId, 0, 20).items()).extracting(SyncRunEventResponse::transitionIndex)
                .containsExactly(2, 1, 0);
        verify(identities).readIdentity(any()); verify(readers).readMovements(any());
    }
    @Test void shouldRejectOuterTransactionBeforeAnyExternalIoOrStateChange() {
        var tx = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> tx.execute(status -> executor.execute(runId))).isInstanceOf(IllegalTransactionStateException.class);
        assertThat(run().getStatus()).isEqualTo(SyncRunStatus.REQUESTED);
        verifyNoInteractions(identities, readers, publication);
    }
    @Test void shouldClaimOnlyOnceAcrossConcurrentWorkers() throws Exception {
        var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> task = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Synthetic test did not start.");
                return lifecycle.claim(runId).isPresent();
            };
            var first = pool.submit(task); var second = pool.submit(task);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
            assertThat(first.get(15, TimeUnit.SECONDS)).isNotEqualTo(second.get(15, TimeUnit.SECONDS));
        }
        assertThat(run().getStatus()).isEqualTo(SyncRunStatus.RUNNING);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sync_run_events WHERE sync_run_id = ?", Long.class, runId)).isEqualTo(2L);
    }
    @Test void shouldRejectMismatchedIdentityBeforeMovementReadOrPublication() {
        when(identities.readIdentity(any())).thenReturn(new ExternalCompanyIdentity("synthetic", "29292929000129"));
        assertThat(executor.execute(runId)).isEqualTo(SyncExecutionOutcome.FAILED);
        assertThat(run().getFailureCode()).isEqualTo(SyncFailureCode.IDENTITY_MISMATCH);
        verifyNoInteractions(readers, publication);
    }
    @Test void shouldKeepReadFailureSanitizedAndNeverInvokePublication() {
        when(readers.readMovements(any())).thenThrow(new IllegalStateException("synthetic private endpoint"));
        assertThat(executor.execute(runId)).isEqualTo(SyncExecutionOutcome.FAILED);
        assertThat(run().getFailureCode()).isEqualTo(SyncFailureCode.READ_FAILED);
        assertThat(run().getReadCount()).isNull();
        verifyNoInteractions(publication);
    }
    @Test void shouldFenceOldFailureAndKeepTerminalFailureIdempotent() {
        var claim = lifecycle.claim(runId).orElseThrow();
        var stale = new ClaimedSyncRun(runId, claim.version() + 1, claim.mode(), claim.request());
        assertThat(lifecycle.failIfCurrent(stale, SyncFailureCode.READ_FAILED)).isEqualTo(SyncFailureDisposition.STALE);
        assertThat(run().getStatus()).isEqualTo(SyncRunStatus.RUNNING);
        assertThat(lifecycle.failIfCurrent(claim, SyncFailureCode.READ_FAILED)).isEqualTo(SyncFailureDisposition.TERMINAL_FAILURE);
        assertThat(lifecycle.failIfCurrent(claim, SyncFailureCode.PUBLICATION_FAILED)).isEqualTo(SyncFailureDisposition.STALE);
        assertThat(run().getFailureCode()).isEqualTo(SyncFailureCode.READ_FAILED);
    }
    @Test void shouldFailClosedForRevokedRequesterAndInactiveSource() {
        jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id = ?", grantId);
        assertThat(executor.execute(runId)).isEqualTo(SyncExecutionOutcome.NOT_CLAIMED);
        assertThat(run().getFailureCode()).isEqualTo(SyncFailureCode.ACCESS_REVOKED);
        verifyNoInteractions(identities, readers, publication);
    }
    @Test void shouldRejectInactiveSourceBeforeExternalIo() {
        jdbc.update("UPDATE accounting_sources SET active = false WHERE id = ?", sourceId);
        assertThat(executor.execute(runId)).isEqualTo(SyncExecutionOutcome.NOT_CLAIMED);
        assertThat(run().getFailureCode()).isEqualTo(SyncFailureCode.SOURCE_NOT_AVAILABLE);
        verifyNoInteractions(identities, readers, publication);
    }
    @Test void shouldNotInventIncrementalOrReconciliationStrategies() {
        control.cancel(actor, sourceId, runId);
        runId = control.request(actor, sourceId, UUID.randomUUID(), request(SyncRunMode.RECONCILIATION)).id();
        assertThat(executor.execute(runId)).isEqualTo(SyncExecutionOutcome.NOT_CLAIMED);
        assertThat(run().getFailureCode()).isEqualTo(SyncFailureCode.UNSUPPORTED_MODE);
        verifyNoInteractions(identities, readers, publication);
    }
    private RequestSyncRun request(SyncRunMode mode) { return new RequestSyncRun(mode, LocalDate.of(2026,7,1), LocalDate.of(2026,7,31)); }
    private SyncRun run() { return runs.findByIdAndAccountingSourceId(runId, sourceId).orElseThrow(); }
}

