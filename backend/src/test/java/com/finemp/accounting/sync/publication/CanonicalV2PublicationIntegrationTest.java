package com.finemp.accounting.sync.publication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.finemp.TestcontainersConfiguration;
import com.finemp.access.*;
import com.finemp.accounting.canonical.v2.*;
import com.finemp.accounting.coverage.AccountingDatasetCoverageService;
import com.finemp.accounting.reading.AccountingCompanyIdentityReader;
import com.finemp.accounting.reading.AccountingSourceReaderRegistry;
import com.finemp.accounting.reading.ExternalCompanyIdentity;
import com.finemp.accounting.source.*;
import com.finemp.accounting.sync.*;
import com.finemp.accountingfirm.AccountingFirm;
import com.finemp.accountingfirm.AccountingFirmRepository;
import com.finemp.client.Client;
import com.finemp.client.ClientRepository;
import com.finemp.connector.ConnectorCredential;
import com.finemp.connector.ConnectorCredentialRepository;
import com.finemp.accounting.sync.control.*;
import com.finemp.accounting.sync.execution.*;
import com.finemp.company.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
        "finemp.sync.control.enabled=true",
        "finemp.sync.executor.enabled=true",
        "finemp.sync.retry.max-attempts=1"
})
@Import(TestcontainersConfiguration.class)
class CanonicalV2PublicationIntegrationTest {
    @Autowired private AccountingPublicationTransaction publication;
    @Autowired private SyncExecutionLifecycle lifecycle;
    @Autowired private SyncControlService control;
    @Autowired private SyncRunRepository runs;
    @Autowired private UserAccountRepository users;
    @Autowired private CompanyRepository companies;
    @Autowired private AccountingFirmRepository firms;
    @Autowired private ClientRepository clients;
    @Autowired private ConnectorCredentialRepository credentials;
    @Autowired private AccountingSourceRepository sources;
    @Autowired private CompanyAccessGrantRepository grants;
    @Autowired private AccountingDatasetCoverageService coverage;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private SyncAgentAuthorization agents;
    @MockitoBean private AccountingCompanyIdentityReader identities;
    @MockitoBean private AccountingSourceReaderRegistry readers;

    private final LocalDate start=LocalDate.of(2026,7,1);
    private final LocalDate end=LocalDate.of(2026,7,31);
    private SyncClaimAgent connector;
    private UUID actor,firmId,clientId,companyId,sourceId,grantId,credentialId;

    @BeforeEach
    void prepare(){
        var user=users.saveAndFlush(new UserAccount(
                "Synthetic V2 Publisher",
                UUID.randomUUID()+"@example.invalid",
                "hash"));
        var firm=firms.saveAndFlush(new AccountingFirm("Synthetic V2 Firm",true));
        var client=clients.saveAndFlush(new Client(firm,"Synthetic V2 Client",true));
        var company=companies.saveAndFlush(new Company(
                client,
                "Synthetic V2 Publisher",
                "41414141000141"));
        var source=new AccountingSource(company,AccountingSourceType.QUESTOR,"synthetic-v2",true);
        source.reserveConnectorRoute();
        source=sources.saveAndFlush(source);
        var grant=grants.saveAndFlush(new CompanyAccessGrant(
                user,
                company,
                CompanyAccessRole.CUSTOM,
                CostCenterScope.ALL,
                Set.of(CompanyPermission.QUESTOR_SYNC)));
        var now=Instant.parse("2026-07-01T10:00:00Z");
        var credential=credentials.saveAndFlush(new ConnectorCredential(
                firm.getId(),
                source.getId(),
                "Synthetic V2 Connector",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                user.getId(),
                now,
                now.plusSeconds(86400)));
        actor=user.getId(); firmId=firm.getId(); clientId=client.getId(); companyId=company.getId();
        sourceId=source.getId(); grantId=grant.getId(); credentialId=credential.getId();
        connector=new SyncClaimAgent(credential.getId(),credential.getVersion());
        when(agents.isCurrent(any(),eq(sourceId))).thenReturn(true);
    }

    @AfterEach
    void clean(){
        jdbc.update("DELETE FROM accounting_entry_source_metadata WHERE accounting_source_id = ?",sourceId);
        jdbc.update("DELETE FROM accounting_daily_debit_credit_totals WHERE accounting_source_id = ?",sourceId);
        jdbc.update("DELETE FROM accounting_monthly_debit_credit_totals WHERE accounting_source_id = ?",sourceId);
        jdbc.update("DELETE FROM accounting_origin_catalog WHERE accounting_source_id = ?",sourceId);
        jdbc.update("DELETE FROM accounting_account_source_attributes WHERE accounting_source_id = ?",sourceId);
        jdbc.update("DELETE FROM accounting_dataset_coverage WHERE accounting_source_id = ?",sourceId);
        jdbc.update("DELETE FROM sync_run_events WHERE sync_run_id IN (SELECT id FROM sync_runs WHERE accounting_source_id = ?)",sourceId);
        jdbc.update("DELETE FROM sync_runs WHERE accounting_source_id = ?",sourceId);
        jdbc.update("DELETE FROM accounting_movements WHERE accounting_source_id = ?",sourceId);
        jdbc.update("DELETE FROM accounting_accounts WHERE accounting_source_id = ?",sourceId);
        jdbc.update("DELETE FROM connector_credentials WHERE id = ?",credentialId);
        jdbc.update("DELETE FROM accounting_sources WHERE id = ?",sourceId);
        jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id = ?",grantId);
        jdbc.update("DELETE FROM company_access_grants WHERE id = ?",grantId);
        jdbc.update("DELETE FROM companies WHERE id = ?",companyId);
        jdbc.update("DELETE FROM clients WHERE id = ?",clientId);
        jdbc.update("DELETE FROM accounting_firms WHERE id = ?",firmId);
        jdbc.update("DELETE FROM user_accounts WHERE id = ?",actor);
    }

    @Test
    void shouldPublishEveryDeclaredV2DatasetAtomicallyAndReplayIdempotently(){
        var first=claim();
        publication.publishConnectorV2(first,batch(first,"Origin A",false));

        var firstRun=run(first);
        assertThat(firstRun.getStatus()).isEqualTo(SyncRunStatus.SUCCEEDED);
        assertThat(firstRun.getReadCount()).isEqualTo(2);
        assertThat(firstRun.getImportedCount()).isEqualTo(2);
        assertThat(firstRun.getSkippedCount()).isZero();

        assertThat(count("accounting_accounts")).isEqualTo(2);
        assertThat(count("accounting_movements")).isEqualTo(2);
        assertThat(count("accounting_account_source_attributes")).isEqualTo(2);
        assertThat(count("accounting_entry_source_metadata")).isEqualTo(1);
        assertThat(count("accounting_daily_debit_credit_totals")).isEqualTo(1);
        assertThat(count("accounting_monthly_debit_credit_totals")).isEqualTo(1);
        assertThat(count("accounting_origin_catalog")).isEqualTo(1);

        assertThat(coverage.read(sourceId,AccountingDataset.CHART).publishedAt()).isNotNull();
        assertThat(coverage.read(sourceId,AccountingDataset.MOVEMENTS).intervals())
                .containsExactly(interval(start,end));
        assertThat(coverage.read(sourceId,AccountingDataset.DAILY_TOTALS).intervals())
                .containsExactly(interval(start,end));
        assertThat(coverage.read(sourceId,AccountingDataset.MONTHLY_TOTALS).intervals())
                .containsExactly(interval(start,end));
        assertThat(coverage.read(sourceId,AccountingDataset.ORIGIN_CATALOG).publishedAt()).isNotNull();

        var replay=claim();
        publication.publishConnectorV2(replay,batch(replay,"Origin A",false));
        var replayRun=run(replay);
        assertThat(replayRun.getImportedCount()).isZero();
        assertThat(replayRun.getSkippedCount()).isEqualTo(2);
        assertThat(count("accounting_movements")).isEqualTo(2);
        assertThat(count("accounting_daily_debit_credit_totals")).isEqualTo(1);
        assertThat(count("accounting_monthly_debit_credit_totals")).isEqualTo(1);
        assertThat(count("accounting_origin_catalog")).isEqualTo(1);
    }

    @Test
    void shouldAcceptLegacyFirebirdRightPaddingWhenV2PublishesEquivalentAccounts(){
        var legacy=claim();
        var observed=new ExternalCompanyIdentity(
                legacy.request().externalCompanyReference(),
                legacy.request().expectedCompanyCnpj());
        publication.publishConnector(
                legacy,
                observed,
                List.of(
                        new com.finemp.accounting.ingestion.ImportedAccountingMovement(
                                "legacy-entry",
                                start,
                                "100",
                                "1.1.100",
                                "Synthetic Asset   ",
                                com.finemp.accounting.movement.AccountingMovementSide.DEBIT,
                                new BigDecimal("10.0000"),
                                "Synthetic history"),
                        new com.finemp.accounting.ingestion.ImportedAccountingMovement(
                                "legacy-entry",
                                start,
                                "200",
                                "3.2.200",
                                "Synthetic Revenue   ",
                                com.finemp.accounting.movement.AccountingMovementSide.CREDIT,
                                new BigDecimal("10.0000"),
                                "Synthetic history")));

        var v2=claim();
        publication.publishConnectorV2(v2,batch(v2,"Origin A",false));

        assertThat(run(v2).getStatus()).isEqualTo(SyncRunStatus.SUCCEEDED);
        assertThat(count("accounting_accounts")).isEqualTo(2);
    }

    @Test
    void shouldRollbackEarlierV2WritesWhenALaterDatasetConflicts(){
        var first=claim();
        publication.publishConnectorV2(first,batch(first,"Origin A",false));

        var conflicting=claim();
        assertThatThrownBy(() -> publication.publishConnectorV2(
                conflicting,
                batch(conflicting,"Origin changed",true)))
                .isInstanceOfSatisfying(
                        SyncPublicationRejectedException.class,
                        error -> assertThat(error.getCode()).isEqualTo(SyncFailureCode.DATA_CONFLICT));

        assertThat(count("accounting_accounts")).isEqualTo(2);
        assertThat(count("accounting_movements")).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM accounting_accounts WHERE accounting_source_id = ? AND external_account_id = '300'",
                Long.class,
                sourceId)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM accounting_movements WHERE accounting_source_id = ? AND source_entry_key = 'entry-2'",
                Long.class,
                sourceId)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT description FROM accounting_origin_catalog WHERE accounting_source_id = ? AND origin_code = 'FI'",
                String.class,
                sourceId)).isEqualTo("Origin A");
        assertThat(run(conflicting).getStatus()).isEqualTo(SyncRunStatus.RUNNING);
    }

    @Test
    void shouldRejectStaleV2FenceWithoutPublishingAnything(){
        var current=claim();
        var stale=new ClaimedSyncRun(
                current.runId(),
                current.version()+1,
                current.mode(),
                current.request(),
                current.agent());

        assertThatThrownBy(() -> publication.publishConnectorV2(stale,batch(current,"Origin A",false)))
                .isInstanceOf(StaleSyncClaimException.class);

        assertThat(count("accounting_accounts")).isZero();
        assertThat(count("accounting_movements")).isZero();
        assertThat(count("accounting_account_source_attributes")).isZero();
        assertThat(coverage.read(sourceId,AccountingDataset.MOVEMENTS).intervals()).isEmpty();
    }

    private ClaimedSyncRun claim(){
        UUID runId=control.request(
                actor,
                sourceId,
                UUID.randomUUID(),
                new RequestSyncRun(SyncRunMode.INITIAL,start,end)).id();
        return lifecycle.claimConnector(runId,connector).orElseThrow();
    }

    private SyncRun run(ClaimedSyncRun claim){
        return runs.findByIdAndAccountingSourceId(claim.runId(),sourceId).orElseThrow();
    }

    private CanonicalAccountingBatch batch(
            ClaimedSyncRun claim,
            String originDescription,
            boolean addNewAccount){
        var accounts=new ArrayList<CanonicalAccountingAccount>();
        accounts.add(account("100","1.1","Synthetic Asset"));
        accounts.add(account("200","3.2","Synthetic Revenue"));
        if(addNewAccount) accounts.add(account("300","1.3","Synthetic New"));

        var entries=new ArrayList<CanonicalAccountingEntry>();
        entries.add(new CanonicalAccountingEntry(
                "entry-1",start,"100","200",new BigDecimal("10.0000"),"1","Synthetic history"));
        if(addNewAccount){
            entries.add(new CanonicalAccountingEntry(
                    "entry-2",start,"300","200",new BigDecimal("1.0000"),"1","Synthetic new"));
        }

        var metadata=new ArrayList<CanonicalEntrySourceMetadata>();
        metadata.add(new CanonicalEntrySourceMetadata(
                "entry-1",
                Instant.parse("2026-07-01T12:00:00Z"),
                "H1","FI","LN","SYN","B1","DOC1","G1","T1","1"));
        if(addNewAccount){
            metadata.add(new CanonicalEntrySourceMetadata(
                    "entry-2",
                    Instant.parse("2026-07-01T12:01:00Z"),
                    null,"FI","LN",null,null,null,null,null,"1"));
        }

        var datasets=Set.of(
                AccountingDataset.CHART,
                AccountingDataset.MOVEMENTS,
                AccountingDataset.DAILY_TOTALS,
                AccountingDataset.MONTHLY_TOTALS,
                AccountingDataset.ORIGIN_CATALOG);

        return new CanonicalAccountingBatch(
                CanonicalAccountingBatch.VERSION,
                new ExternalCompanyIdentity(
                        claim.request().externalCompanyReference(),
                        claim.request().expectedCompanyCnpj()),
                new SourceCapabilities(
                        datasets,
                        AmountSignPolicy.NON_NEGATIVE_WITH_SIDE,
                        true),
                new CanonicalChartSnapshot(
                        Instant.parse("2026-07-31T23:59:59Z"),
                        accounts),
                new CanonicalEntriesDataset(start,end,entries,metadata),
                List.of(new CanonicalAccountDailyDebitCreditTotals(
                        "1","100",start,"LN",new BigDecimal("10.0000"),BigDecimal.ZERO)),
                List.of(new CanonicalAccountMonthlyDebitCreditTotals(
                        "1","100",YearMonth.of(2026,7),"LN",new BigDecimal("10.0000"),BigDecimal.ZERO)),
                List.of(new CanonicalOriginCatalogEntry("FI",originDescription)),
                null,
                null);
    }

    private CanonicalAccountingAccount account(String id,String classification,String description){
        return new CanonicalAccountingAccount(
                id,
                classification,
                description,
                new SourceAccountCodes("A","C","D","B","S",null,null));
    }

    private long count(String table){
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM "+table+" WHERE accounting_source_id = ?",
                Long.class,
                sourceId);
    }

    private com.finemp.accounting.coverage.AccountingDatasetCoverageSnapshot.Interval interval(
            LocalDate from,
            LocalDate to){
        return new com.finemp.accounting.coverage.AccountingDatasetCoverageSnapshot.Interval(from,to);
    }
}
