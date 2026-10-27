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

@SpringBootTest(properties = {"finemp.sync.control.enabled=true", "finemp.sync.executor.enabled=true",
        "finemp.sync.retry.max-attempts=3", "finemp.sync.retry.lease-seconds=300", "finemp.sync.retry.initial-delay-seconds=30"})
@Import(TestcontainersConfiguration.class)
class SyncRetryIntegrationTest {
    @Autowired private SyncRunExecutor executor;
    @Autowired private SyncExecutionLifecycle lifecycle;
    @Autowired private SyncPublicationPort publication;
    @Autowired private SyncControlService control;
    @Autowired private SyncRunRepository runs;
    @Autowired private SyncAttemptRepository attempts;
    @Autowired private SyncAttemptQueryService attemptQuery;
    @Autowired private UserAccountRepository users;
    @Autowired private CompanyRepository companies;
    @Autowired private AccountingSourceRepository sources;
    @Autowired private CompanyAccessGrantRepository grants;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean(name = "identityClock") private Clock clock;
    @MockitoBean private AccountingCompanyIdentityReader identities;
    @MockitoBean private AccountingSourceReaderRegistry readers;
    private final Instant initial = Instant.parse("2026-07-01T12:00:00Z");
    private UUID actor, companyId, sourceId, grantId, runId;

    @BeforeEach void prepare() {
        at(0);
        var user = users.saveAndFlush(new UserAccount("Synthetic Retry", UUID.randomUUID() + "@example.invalid", "hash"));
        var company = companies.saveAndFlush(new Company("Synthetic Retry", "41414141000141"));
        var source = sources.saveAndFlush(new AccountingSource(company, AccountingSourceType.QUESTOR, "synthetic", true));
        var grant = grants.saveAndFlush(new CompanyAccessGrant(user, company, CompanyAccessRole.CUSTOM, CostCenterScope.ALL, Set.of(CompanyPermission.QUESTOR_SYNC)));
        actor = user.getId(); companyId = company.getId(); sourceId = source.getId(); grantId = grant.getId();
        runId = control.request(actor, sourceId, UUID.randomUUID(), new RequestSyncRun(SyncRunMode.INITIAL,
                LocalDate.of(2026,7,1), LocalDate.of(2026,7,31))).id();
        when(identities.readIdentity(any())).thenReturn(new ExternalCompanyIdentity("synthetic", "41414141000141"));
        when(readers.readMovements(any())).thenReturn(List.of());
    }
    @AfterEach void cleanOnlyOwnSyntheticRows() {
        jdbc.update("DELETE FROM sync_run_events WHERE sync_run_id = ?", runId);
        jdbc.update("DELETE FROM sync_runs WHERE id = ?", runId);
        jdbc.update("DELETE FROM accounting_movements WHERE accounting_source_id = ?", sourceId);
        jdbc.update("DELETE FROM accounting_accounts WHERE accounting_source_id = ?", sourceId);
        jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id = ?", grantId);
        jdbc.update("DELETE FROM company_access_grants WHERE id = ?", grantId);
        jdbc.update("DELETE FROM accounting_sources WHERE id = ?", sourceId);
        jdbc.update("DELETE FROM companies WHERE id = ?", companyId);
        jdbc.update("DELETE FROM user_accounts WHERE id = ?", actor);
    }
    @Test void shouldWaitForDueTimeThenPublishUsingANewFenceAndKeepOneOverallTransitionPerPhase() {
        when(readers.readMovements(any())).thenThrow(new IllegalStateException("synthetic private detail"));
        assertThat(executor.execute(runId)).isEqualTo(SyncExecutionOutcome.RETRY_SCHEDULED);
        var first = last();
        assertThat(first.getFailureCode()).isEqualTo(SyncFailureCode.READ_FAILED);
        assertThat(first.getNextAttemptAt()).isEqualTo(initial.plusSeconds(30));
        assertThat(run().getStatus()).isEqualTo(SyncRunStatus.RUNNING);
        at(29); assertThat(executor.execute(runId)).isEqualTo(SyncExecutionOutcome.NOT_CLAIMED);
        // doReturn avoids invoking the preceding throwing stub while restubbing.
        doReturn(List.of()).when(readers).readMovements(any());
        at(30); assertThat(executor.execute(runId)).isEqualTo(SyncExecutionOutcome.PUBLISHED);
        assertThat(last().getStatus()).isEqualTo(SyncAttemptStatus.SUCCEEDED);
        assertThat(last().getFenceVersion()).isGreaterThan(first.getFenceVersion());
        assertThat(run().getAttemptNumber()).isEqualTo(2);
        assertThat(control.events(actor, sourceId, runId, 0, 20).items()).extracting(SyncRunEventResponse::transitionIndex)
                .containsExactly(2, 1, 0);
    }
    @Test void shouldQuarantineEveryLateWorkerOperationBeforeAndAfterReclaim() {
        var old = lifecycle.claim(runId).orElseThrow();
        at(300);
        assertThat(lifecycle.renewLease(old)).isFalse();
        assertThat(lifecycle.failIfCurrent(old, SyncFailureCode.READ_FAILED)).isEqualTo(SyncFailureDisposition.STALE);
        assertThatThrownBy(() -> publication.publish(old, List.of())).isInstanceOf(StaleSyncClaimException.class);
        assertThat(lifecycle.claim(runId)).isEmpty(); // Expire and schedule, do not bypass backoff.
        assertThat(last().getStatus()).isEqualTo(SyncAttemptStatus.EXPIRED);
        at(330); var next = lifecycle.claim(runId).orElseThrow();
        assertThat(next.version()).isGreaterThan(old.version());
        assertThat(lifecycle.renewLease(old)).isFalse();
        assertThatThrownBy(() -> publication.publish(old, List.of())).isInstanceOf(StaleSyncClaimException.class);
        publication.publish(next, List.of());
        assertThat(run().getStatus()).isEqualTo(SyncRunStatus.SUCCEEDED);
    }
    @Test void shouldRenewOnlyTheCurrentLiveAttemptWithoutChangingTheRunFence() {
        var claim = lifecycle.claim(runId).orElseThrow();
        at(200); assertThat(lifecycle.renewLease(claim)).isTrue();
        assertThat(run().getVersion()).isEqualTo(claim.version());
        assertThat(last().getLeaseExpiresAt()).isEqualTo(initial.plusSeconds(500));
        at(300); assertThat(lifecycle.claim(runId)).isEmpty();
        publication.publish(claim, List.of());
        assertThat(last().getStatus()).isEqualTo(SyncAttemptStatus.SUCCEEDED);
        assertThat(lifecycle.renewLease(claim)).isFalse();
    }
    @Test void shouldExhaustBoundedAttemptsAndRetainTheLastSpecificCauseWithoutExposingExternalText() {
        when(readers.readMovements(any())).thenThrow(new IllegalStateException("synthetic private detail"));
        assertThat(executor.execute(runId)).isEqualTo(SyncExecutionOutcome.RETRY_SCHEDULED);
        at(30); assertThat(executor.execute(runId)).isEqualTo(SyncExecutionOutcome.RETRY_SCHEDULED);
        at(89); assertThat(executor.execute(runId)).isEqualTo(SyncExecutionOutcome.NOT_CLAIMED);
        at(90); assertThat(executor.execute(runId)).isEqualTo(SyncExecutionOutcome.FAILED);
        assertThat(run().getFailureCode()).isEqualTo(SyncFailureCode.RETRY_LIMIT);
        assertThat(run().getAttemptNumber()).isEqualTo(3);
        assertThat(last().getFailureCode()).isEqualTo(SyncFailureCode.READ_FAILED);
        assertThat(last().getNextAttemptAt()).isNull();
        assertThat(executor.execute(runId)).isEqualTo(SyncExecutionOutcome.NOT_CLAIMED);
    }
    @Test void shouldNeverRetryIdentityPayloadResourceOrConflictFailures() {
        when(identities.readIdentity(any())).thenReturn(new ExternalCompanyIdentity("other", "41414141000141"));
        assertThat(executor.execute(runId)).isEqualTo(SyncExecutionOutcome.FAILED);
        assertThat(run().getFailureCode()).isEqualTo(SyncFailureCode.IDENTITY_MISMATCH);
        assertThat(last().getNextAttemptAt()).isNull();
        verifyNoInteractions(readers);
    }
    @Test void shouldRevalidateRequesterBeforeRetryAndStopRenewingAfterRevocation() {
        var claim = lifecycle.claim(runId).orElseThrow();
        jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id = ?", grantId);
        at(1); assertThat(lifecycle.renewLease(claim)).isFalse();
        assertThat(lifecycle.failIfCurrent(claim, SyncFailureCode.READ_FAILED)).isEqualTo(SyncFailureDisposition.RETRY_SCHEDULED);
        at(31); assertThat(lifecycle.claim(runId)).isEmpty();
        assertThat(run().getFailureCode()).isEqualTo(SyncFailureCode.ACCESS_REVOKED);
        verifyNoInteractions(identities, readers);
    }
    @Test void shouldReclaimOnlyOnceAcrossConcurrentWorkers() throws Exception {
        var first = lifecycle.claim(runId).orElseThrow();
        lifecycle.failIfCurrent(first, SyncFailureCode.READ_FAILED); at(30);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> work = () -> { start.await(10, TimeUnit.SECONDS); return lifecycle.claim(runId).isPresent(); };
            var a = pool.submit(work); var b = pool.submit(work); start.countDown();
            assertThat(a.get(15, TimeUnit.SECONDS)).isNotEqualTo(b.get(15, TimeUnit.SECONDS));
        }
        assertThat(run().getAttemptNumber()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sync_attempts WHERE sync_run_id = ? AND status = 'RUNNING'", Long.class, runId)).isEqualTo(1);
    }
    @Test void shouldPageAttemptHistoryOnlyWithinAnAuthorizedSourceAndRun() {
        var first = lifecycle.claim(runId).orElseThrow();
        lifecycle.failIfCurrent(first, SyncFailureCode.READ_FAILED); at(30);
        executor.execute(runId);
        assertThat(attemptQuery.list(actor, sourceId, runId, 0, 1).items()).extracting(SyncAttemptResponse::attemptNumber).containsExactly(2);
        assertThat(attemptQuery.list(actor, sourceId, runId, 1, 1).items()).extracting(SyncAttemptResponse::attemptNumber).containsExactly(1);
        assertThatThrownBy(() -> attemptQuery.list(actor, sourceId, UUID.randomUUID(), 0, 20))
                .isInstanceOf(com.finemp.administration.SaasResourceNotFoundException.class);
        assertThatThrownBy(() -> attemptQuery.list(UUID.randomUUID(), sourceId, runId, 0, 20))
                .isInstanceOf(com.finemp.administration.OrganizationAccessDeniedException.class);
        assertThatThrownBy(() -> attemptQuery.list(actor, sourceId, runId, 0, 101)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void shouldSelectOnlyQueuedExpiredOrDueIdsAndExcludeLiveBackoffAndTerminalRuns() {
        var candidates = new com.finemp.accounting.sync.worker.JdbcSyncCandidateSource(jdbc, clock);
        assertThat(candidates.due(8)).contains(runId);
        assertThat(candidates.due(8, Set.of(runId))).doesNotContain(runId);
        var claim = lifecycle.claim(runId).orElseThrow();
        assertThat(candidates.due(8)).doesNotContain(runId);
        at(300); assertThat(candidates.due(8)).contains(runId);
        lifecycle.claim(runId); // Marks expiry and starts backoff.
        assertThat(candidates.due(8)).doesNotContain(runId);
        at(330); assertThat(candidates.due(8)).contains(runId);
        var next = lifecycle.claim(runId).orElseThrow();
        publication.publish(next, List.of());
        assertThat(candidates.due(8)).doesNotContain(runId);
        assertThatThrownBy(() -> candidates.due(9)).isInstanceOf(IllegalArgumentException.class);
    }
    private void at(long seconds) { when(clock.instant()).thenReturn(initial.plusSeconds(seconds)); }
    private SyncRun run() { return runs.findByIdAndAccountingSourceId(runId, sourceId).orElseThrow(); }
    private SyncAttempt last() { return attempts.findFirstBySyncRunIdOrderByAttemptNumberDesc(runId).orElseThrow(); }
}
