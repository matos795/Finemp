package com.finemp.accounting.sync;

import static org.assertj.core.api.Assertions.*;
import com.finemp.TestcontainersConfiguration;
import com.finemp.access.*;
import com.finemp.accounting.source.*;
import com.finemp.accounting.sync.control.*;
import com.finemp.company.*;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = "finemp.sync.control.enabled=true")
@Import(TestcontainersConfiguration.class)
class SyncControlConcurrencyIntegrationTest {
    @Autowired private SyncControlService service;
    @Autowired private UserAccountRepository users;
    @Autowired private CompanyRepository companies;
    @Autowired private AccountingSourceRepository sources;
    @Autowired private CompanyAccessGrantRepository grants;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    private UUID actor, sourceId, companyId;
    @BeforeEach void prepare() {
        var user = users.saveAndFlush(new UserAccount("Synthetic Concurrent Sync", UUID.randomUUID() + "@example.invalid", "hash"));
        var company = companies.saveAndFlush(new Company("Synthetic Concurrent Sync", "24242424000124"));
        var source = sources.saveAndFlush(new AccountingSource(company, AccountingSourceType.QUESTOR, "synthetic", true));
        grants.saveAndFlush(new CompanyAccessGrant(user, company, CompanyAccessRole.CUSTOM, CostCenterScope.ALL, Set.of(CompanyPermission.QUESTOR_SYNC)));
        actor = user.getId(); sourceId = source.getId(); companyId = company.getId();
    }
    @AfterEach void cleanOnlyOwnSyntheticRows() {
        jdbc.update("DELETE FROM sync_run_events WHERE sync_run_id IN (SELECT id FROM sync_runs WHERE accounting_source_id = ?)", sourceId);
        jdbc.update("DELETE FROM sync_runs WHERE accounting_source_id = ?", sourceId);
        jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id IN (SELECT id FROM company_access_grants WHERE company_id = ?)", companyId);
        jdbc.update("DELETE FROM company_access_grants WHERE company_id = ?", companyId);
        jdbc.update("DELETE FROM accounting_sources WHERE id = ?", sourceId);
        jdbc.update("DELETE FROM companies WHERE id = ?", companyId);
        jdbc.update("DELETE FROM user_accounts WHERE id = ?", actor);
    }

    @Test void shouldReturnOneRunAndOneEventForConcurrentIdenticalRequests() throws Exception {
        var key = UUID.randomUUID(); var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<UUID> task = () -> { await(ready, start); return service.request(actor, sourceId, key, request()).id(); };
            var first = pool.submit(task); var second = pool.submit(task);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
            assertThat(first.get(15, TimeUnit.SECONDS)).isNotNull().isEqualTo(second.get(15, TimeUnit.SECONDS));
        }
        assertThat(countRuns()).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sync_run_events WHERE sync_run_id IN"
                + " (SELECT id FROM sync_runs WHERE accounting_source_id = ?)", Long.class, sourceId)).isEqualTo(1L);
    }
    @Test void shouldAcceptOnlyOneOfConcurrentDifferentRequestsForTheSameSource() throws Exception {
        var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> task = () -> {
                await(ready, start);
                try { service.request(actor, sourceId, UUID.randomUUID(), request()); return true; }
                catch (SyncControlConflictException expected) {
                    assertThat(expected.getReason()).isEqualTo(SyncControlConflictException.Reason.ACTIVE_RUN_EXISTS);
                    return false;
                }
            };
            var first = pool.submit(task); var second = pool.submit(task);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
            assertThat(first.get(15, TimeUnit.SECONDS)).isNotEqualTo(second.get(15, TimeUnit.SECONDS));
        }
        assertThat(countRuns()).isEqualTo(1L);
    }
    @Test void shouldRejectIncompatibleOuterIsolationRatherThanReplayAgainstAnOldSnapshot() {
        var tx = new TransactionTemplate(transactionManager);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        assertThatThrownBy(() -> tx.execute(status -> service.request(actor, sourceId, UUID.randomUUID(), request())))
                .isInstanceOf(IllegalStateException.class);
        assertThat(countRuns()).isZero();
    }
    private long countRuns() { return jdbc.queryForObject("SELECT COUNT(*) FROM sync_runs WHERE accounting_source_id = ?", Long.class, sourceId); }
    private RequestSyncRun request() { return new RequestSyncRun(SyncRunMode.INITIAL, LocalDate.of(2026,7,1), LocalDate.of(2026,7,31)); }
    private void await(CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Synthetic test did not start.");
    }
}

