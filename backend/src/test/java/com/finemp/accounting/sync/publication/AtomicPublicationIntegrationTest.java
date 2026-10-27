package com.finemp.accounting.sync.publication;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.finemp.TestcontainersConfiguration;
import com.finemp.access.*;
import com.finemp.accounting.account.*;
import com.finemp.accounting.ingestion.ImportedAccountingMovement;
import com.finemp.accounting.movement.*;
import com.finemp.accounting.reading.*;
import com.finemp.accounting.source.*;
import com.finemp.accounting.sync.*;
import com.finemp.accounting.sync.control.*;
import com.finemp.accounting.sync.execution.*;
import com.finemp.company.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {"finemp.sync.control.enabled=true", "finemp.sync.executor.enabled=true", "finemp.sync.retry.max-attempts=1"})
@Import(TestcontainersConfiguration.class)
class AtomicPublicationIntegrationTest {
    @Autowired private SyncRunExecutor executor;
    @Autowired private SyncExecutionLifecycle lifecycle;
    @Autowired private SyncPublicationPort publication;
    @Autowired private SyncControlService control;
    @Autowired private SyncRunRepository runs;
    @Autowired private UserAccountRepository users;
    @Autowired private CompanyRepository companies;
    @Autowired private AccountingSourceRepository sources;
    @Autowired private CompanyAccessGrantRepository grants;
    @Autowired private AccountingAccountRepository accounts;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private org.springframework.transaction.PlatformTransactionManager transactionManager;
    @MockitoBean private AccountingCompanyIdentityReader identities;
    @MockitoBean private AccountingSourceReaderRegistry readers;
    private final LocalDate start = LocalDate.of(2026,7,1), end = LocalDate.of(2026,7,31);
    private final List<UUID> ownedSources = new ArrayList<>();
    private UUID actor, companyId, sourceId, grantId;

    @BeforeEach void prepare() {
        var user = users.saveAndFlush(new UserAccount("Synthetic Atomic Publisher", UUID.randomUUID() + "@example.invalid", "hash"));
        var company = companies.saveAndFlush(new Company("Synthetic Atomic Publisher", "31313131000131"));
        var source = sources.saveAndFlush(new AccountingSource(company, AccountingSourceType.QUESTOR, "synthetic", true));
        var grant = grants.saveAndFlush(new CompanyAccessGrant(user, company, CompanyAccessRole.CUSTOM, CostCenterScope.ALL, Set.of(CompanyPermission.QUESTOR_SYNC)));
        actor = user.getId(); companyId = company.getId(); sourceId = source.getId(); grantId = grant.getId(); ownedSources.add(sourceId);
        when(identities.readIdentity(any())).thenReturn(new ExternalCompanyIdentity("synthetic", "31.313.131/0001-31"));
        when(readers.readMovements(any())).thenReturn(rows());
    }
    @AfterEach void cleanOnlyOwnSyntheticRows() {
        for (var source : ownedSources) {
            jdbc.update("DELETE FROM sync_run_events WHERE sync_run_id IN (SELECT id FROM sync_runs WHERE accounting_source_id = ?)", source);
            jdbc.update("DELETE FROM sync_runs WHERE accounting_source_id = ?", source);
            jdbc.update("DELETE FROM accounting_movements WHERE accounting_source_id = ?", source);
            jdbc.update("DELETE FROM accounting_accounts WHERE accounting_source_id = ?", source);
            jdbc.update("DELETE FROM accounting_sources WHERE id = ?", source);
        }
        jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id = ?", grantId);
        jdbc.update("DELETE FROM company_access_grants WHERE id = ?", grantId);
        jdbc.update("DELETE FROM companies WHERE id = ?", companyId);
        jdbc.update("DELETE FROM user_accounts WHERE id = ?", actor);
    }

    @Test void shouldPublishMovementsCountersAndSuccessTogetherAndSkipOnlyIdenticalReplay() {
        var payload = new ArrayList<>(rows()); payload.add(rows().getFirst());
        when(readers.readMovements(any())).thenReturn(payload);
        var first = execute(sourceId);
        assertThat(first.getStatus()).isEqualTo(SyncRunStatus.SUCCEEDED);
        assertThat(first.getReadCount()).isEqualTo(3); assertThat(first.getImportedCount()).isEqualTo(2); assertThat(first.getSkippedCount()).isEqualTo(1);
        var replay = execute(sourceId);
        assertThat(replay.getImportedCount()).isZero(); assertThat(replay.getSkippedCount()).isEqualTo(3);
        assertThat(movementCount(sourceId)).isEqualTo(2); assertThat(accountCount(sourceId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM accounting_movements movement JOIN accounting_accounts account ON account.id = movement.account_id"
                + " WHERE movement.accounting_source_id = ? AND movement.accounting_source_id <> account.accounting_source_id", Long.class, sourceId)).isZero();
    }
    @Test void shouldKeepIdenticalExternalCodesAndEntryKeysIndependentAcrossSources() {
        execute(sourceId);
        var other = sources.saveAndFlush(new AccountingSource(companies.getReferenceById(companyId), AccountingSourceType.QUESTOR, "synthetic-other", true));
        ownedSources.add(other.getId());
        when(identities.readIdentity(argThat(request -> request != null && request.accountingSourceId().equals(other.getId()))))
                .thenReturn(new ExternalCompanyIdentity("synthetic-other", "31313131000131"));
        var result = execute(other.getId());
        assertThat(result.getImportedCount()).isEqualTo(2);
        assertThat(movementCount(sourceId)).isEqualTo(2); assertThat(movementCount(other.getId())).isEqualTo(2);
    }
    @Test void shouldPreservePriorPublicationAndRejectChangedIdentityBeforeCreatingAnyNewAccount() {
        execute(sourceId);
        when(readers.readMovements(any())).thenReturn(List.of(
                row("journal", "100", "1.100", "Synthetic Asset", AccountingMovementSide.DEBIT, "11", start),
                row("new-journal", "300", "3.300", "New Synthetic", AccountingMovementSide.CREDIT, "1", start)));
        var result = execute(sourceId);
        assertThat(result.getFailureCode()).isEqualTo(SyncFailureCode.DATA_CONFLICT);
        assertThat(result.getReadCount()).isNull();
        assertThat(movementCount(sourceId)).isEqualTo(2); assertThat(accountCount(sourceId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT amount FROM accounting_movements WHERE accounting_source_id = ? AND side = 'DEBIT'",
                BigDecimal.class, sourceId)).isEqualByComparingTo("10.0000");
    }
    @Test void shouldRejectInvalidLaterRowWithoutPublishingAnyEarlierRow() {
        when(readers.readMovements(any())).thenReturn(List.of(rows().getFirst(),
                row("bad", "300", "3.300", "Synthetic", AccountingMovementSide.CREDIT, "1", end.plusDays(1))));
        var result = execute(sourceId);
        assertThat(result.getFailureCode()).isEqualTo(SyncFailureCode.INVALID_PAYLOAD);
        assertThat(movementCount(sourceId)).isZero(); assertThat(accountCount(sourceId)).isZero();
    }
    @Test void shouldFenceStaleContextAndRecheckPermissionAtPublicationTime() {
        var runId = request(sourceId); var claim = lifecycle.claim(runId).orElseThrow();
        assertThatThrownBy(() -> publication.publish(new ClaimedSyncRun(runId, claim.version() + 1, claim.mode(), claim.request()), rows()))
                .isInstanceOf(StaleSyncClaimException.class);
        jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id = ?", grantId);
        assertThatThrownBy(() -> publication.publish(claim, rows())).isInstanceOfSatisfying(SyncPublicationRejectedException.class,
                error -> assertThat(error.getCode()).isEqualTo(SyncFailureCode.ACCESS_REVOKED));
        assertThat(movementCount(sourceId)).isZero(); assertThat(accountCount(sourceId)).isZero();
        assertThat(runs.findByIdAndAccountingSourceId(runId, sourceId).orElseThrow().getStatus()).isEqualTo(SyncRunStatus.RUNNING);
    }
    @Test void shouldRollbackAccountsMovementsAndSuccessWhenTerminalAuditFails() {
        var runId = request(sourceId);
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String function = "f_pub_fail_" + suffix, trigger = "t_pub_fail_" + suffix;
        jdbc.execute("CREATE FUNCTION " + function + "() RETURNS TRIGGER LANGUAGE plpgsql AS $$ BEGIN IF NEW.sync_run_id = '"
                + runId + "'::uuid AND NEW.status = 'SUCCEEDED' THEN RAISE EXCEPTION 'Synthetic terminal audit failure'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER " + trigger + " BEFORE INSERT ON sync_run_events FOR EACH ROW EXECUTE FUNCTION " + function + "()");
        try {
            assertThat(executor.execute(runId)).isEqualTo(SyncExecutionOutcome.FAILED);
            var result = runs.findByIdAndAccountingSourceId(runId, sourceId).orElseThrow();
            assertThat(result.getFailureCode()).isEqualTo(SyncFailureCode.PUBLICATION_FAILED);
            assertThat(result.getReadCount()).isNull();
            assertThat(movementCount(sourceId)).isZero(); assertThat(accountCount(sourceId)).isZero();
        } finally {
            jdbc.execute("DROP TRIGGER " + trigger + " ON sync_run_events");
            jdbc.execute("DROP FUNCTION " + function + "()");
        }
    }
    @Test void shouldFillOwnershipForLegacyWritersAndRejectWrongSourceOrDuplicateIdentityInPostgres() {
        var account = accounts.saveAndFlush(new AccountingAccount(sources.getReferenceById(sourceId), "legacy", "legacy", "Synthetic Legacy"));
        var movementId = UUID.randomUUID();
        jdbc.update("INSERT INTO accounting_movements(id, account_id, source_entry_key, side, accounting_date, amount) VALUES (?, ?, 'synthetic-legacy', 'DEBIT', ?, 1)",
                movementId, account.getId(), start);
        assertThat(jdbc.queryForObject("SELECT accounting_source_id FROM accounting_movements WHERE id = ?", UUID.class, movementId)).isEqualTo(sourceId);
        var other = sources.saveAndFlush(new AccountingSource(companies.getReferenceById(companyId), AccountingSourceType.QUESTOR, "synthetic-other", true));
        ownedSources.add(other.getId());
        assertThatThrownBy(() -> jdbc.update("INSERT INTO accounting_movements(id, account_id, accounting_source_id, source_entry_key, side, accounting_date, amount)"
                + " VALUES (?, ?, ?, 'wrong-owner', 'DEBIT', ?, 1)", UUID.randomUUID(), account.getId(), other.getId(), start))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO accounting_movements(id, account_id, source_entry_key, side, accounting_date, amount)"
                + " VALUES (?, ?, 'synthetic-legacy', 'DEBIT', ?, 1)", UUID.randomUUID(), account.getId(), start))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test void shouldUseBoundedLookupChunksForMoreThanOneThousandCanonicalRows() {
        var payload = new ArrayList<ImportedAccountingMovement>();
        for (int i = 0; i < 1001; i++) payload.add(row("synthetic-" + i, "100", "1.100", "Synthetic Asset", AccountingMovementSide.DEBIT, "1", start));
        when(readers.readMovements(any())).thenReturn(payload);
        var result = execute(sourceId);
        assertThat(result.getImportedCount()).isEqualTo(1001); assertThat(movementCount(sourceId)).isEqualTo(1001);
        assertThat(execute(sourceId).getSkippedCount()).isEqualTo(1001);
    }
    @Test void shouldRejectOuterTransactionsBeforeEnteringTheAtomicPublicationBoundary() {
        var runId = request(sourceId); var claim = lifecycle.claim(runId).orElseThrow();
        var tx = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> tx.execute(status -> { publication.publish(claim, rows()); return null; }))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        assertThat(movementCount(sourceId)).isZero(); assertThat(accountCount(sourceId)).isZero();
    }

    private SyncRun execute(UUID source) {
        var id = request(source); executor.execute(id); return runs.findByIdAndAccountingSourceId(id, source).orElseThrow();
    }
    private UUID request(UUID source) { return control.request(actor, source, UUID.randomUUID(), new RequestSyncRun(SyncRunMode.INITIAL, start, end)).id(); }
    private long movementCount(UUID source) { return jdbc.queryForObject("SELECT COUNT(*) FROM accounting_movements WHERE accounting_source_id = ?", Long.class, source); }
    private long accountCount(UUID source) { return jdbc.queryForObject("SELECT COUNT(*) FROM accounting_accounts WHERE accounting_source_id = ?", Long.class, source); }
    private List<ImportedAccountingMovement> rows() {
        return List.of(row("journal", "100", "1.100", "Synthetic Asset", AccountingMovementSide.DEBIT, "10", start),
                row("journal", "200", "3.200", "Synthetic Revenue", AccountingMovementSide.CREDIT, "10", start));
    }
    private ImportedAccountingMovement row(String key, String account, String accountKey, String description,
            AccountingMovementSide side, String amount, LocalDate date) {
        return new ImportedAccountingMovement(key, date, account, accountKey, description, side, new BigDecimal(amount), "Synthetic history");
    }
}

