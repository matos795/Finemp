package com.finemp.accounting.v2read;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.finemp.TestcontainersConfiguration;
import com.finemp.access.CompanyAccessGrant;
import com.finemp.access.CompanyAccessGrantRepository;
import com.finemp.access.CompanyAccessRole;
import com.finemp.access.CompanyPermission;
import com.finemp.access.CostCenterScope;
import com.finemp.access.PlatformPermission;
import com.finemp.access.UserAccount;
import com.finemp.access.UserAccountRepository;
import com.finemp.accounting.account.AccountingAccount;
import com.finemp.accounting.account.AccountingAccountRepository;
import com.finemp.accounting.canonical.persistence.CanonicalV2DatasetStore;
import com.finemp.accounting.canonical.v2.AccountingDataset;
import com.finemp.accounting.canonical.v2.CanonicalAccountDailyDebitCreditTotals;
import com.finemp.accounting.canonical.v2.CanonicalAccountMonthlyDebitCreditTotals;
import com.finemp.accounting.canonical.v2.CanonicalAccountingAccount;
import com.finemp.accounting.canonical.v2.CanonicalEntrySourceMetadata;
import com.finemp.accounting.canonical.v2.CanonicalOriginCatalogEntry;
import com.finemp.accounting.canonical.v2.SourceAccountCodes;
import com.finemp.accounting.coverage.AccountingDatasetCoverageService;
import com.finemp.accounting.movement.AccountingMovement;
import com.finemp.accounting.movement.AccountingMovementRepository;
import com.finemp.accounting.movement.AccountingMovementSide;
import com.finemp.accounting.source.AccountingSource;
import com.finemp.accounting.source.AccountingSourceRepository;
import com.finemp.accounting.source.AccountingSourceType;
import com.finemp.company.Company;
import com.finemp.company.CompanyRepository;
import com.finemp.security.UserAccountPrincipal;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/**
 * C2.8 (#474): leitura HTTP do domínio contábil v2. Dados sintéticos, gravados
 * pelos mesmos serviços da publicação (store canônico e cobertura).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AccountingV2ReadApiHttpIntegrationTest {

    private static final String CHART = "/api/companies/{companyId}/accounting-chart";
    private static final String TOTALS = "/api/companies/{companyId}/accounting-debit-credit-totals";
    private static final String COVERAGE = "/api/companies/{companyId}/accounting-dataset-coverage";
    private static final String CATALOG = "/api/companies/{companyId}/accounting-origin-catalog";
    private static final String MOVEMENTS = "/api/companies/{companyId}/accounting-movements/search";
    private static final String IMPORTED_ACCOUNTS = "/api/companies/{companyId}/accounting-accounts";
    private static final String READABLE_SOURCES = "/api/companies/{companyId}/accounting-sources/readable";
    private static final Instant CHART_PUBLISHED_AT = Instant.parse("2026-03-31T12:00:00Z");

    @Autowired private MockMvc mvc;
    @Autowired private UserAccountRepository users;
    @Autowired private CompanyRepository companies;
    @Autowired private CompanyAccessGrantRepository grants;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AccountingSourceRepository sources;
    @Autowired private AccountingAccountRepository accounts;
    @Autowired private AccountingMovementRepository movements;
    @Autowired private CanonicalV2DatasetStore store;
    @Autowired private AccountingDatasetCoverageService coverage;

    private Company company;
    private Company otherCompany;
    private AccountingSource source;
    private AccountingSource unpublishedSource;
    private AccountingSource foreignSource;
    private AccountingAccount cash;
    private AccountingAccount revenue;
    private AccountingAccount observedOnly;
    private UserAccount ledgerReader;
    private UserAccount chartReader;

    @BeforeEach
    void prepare() {
        company = companies.saveAndFlush(new Company("Synthetic V2 Company", "61616161000161"));
        otherCompany = companies.saveAndFlush(new Company("Synthetic V2 Foreign", "62626262000162"));
        source = sources.saveAndFlush(new AccountingSource(company, AccountingSourceType.QUESTOR, "v2-a", true));
        unpublishedSource = sources.saveAndFlush(new AccountingSource(company, AccountingSourceType.QUESTOR, "v2-b", true));
        foreignSource = sources.saveAndFlush(new AccountingSource(otherCompany, AccountingSourceType.QUESTOR, "v2-x", true));

        cash = chartAccount(source, "10", "1.1.01.001", "Caixa sintético", "A");
        revenue = chartAccount(source, "30", "3.1.01.001", "Receita sintética", "A");
        chartAccount(source, "1", "1", "Ativo sintético", "S");
        observedOnly = accounts.saveAndFlush(new AccountingAccount(source, "99", "observed.99", "Conta observada"));
        coverage.recordSnapshot(source.getId(), AccountingDataset.CHART, null, CHART_PUBLISHED_AT);

        // Atributos de plano sem cobertura CHART: não podem aparecer como plano completo.
        chartAccount(unpublishedSource, "10", "1.1.01.001", "Caixa sem cobertura", "A");

        AccountingAccount foreignCash = chartAccount(foreignSource, "10", "1.1.01.001", "Caixa estrangeiro", "A");
        coverage.recordSnapshot(foreignSource.getId(), AccountingDataset.CHART, null, CHART_PUBLISHED_AT);
        movement(foreignCash, "FX1", AccountingMovementSide.DEBIT, "2026-03-10", "7.0000");
        daily(foreignSource, "1", "10", "2026-03-10", "LN", "7.0000", "0.0000");
        catalog(foreignSource, "9", "Origem estrangeira");
        coverage.recordSnapshot(foreignSource.getId(), AccountingDataset.ORIGIN_CATALOG, null, CHART_PUBLISHED_AT);

        ledgerReader = users.saveAndFlush(new UserAccount("Synthetic V2 Ledger", UUID.randomUUID() + "@example.invalid", "hash"));
        grant(ledgerReader, company, CompanyPermission.ACCOUNTING_MOVEMENT_VIEW);
        chartReader = users.saveAndFlush(new UserAccount("Synthetic V2 Chart", UUID.randomUUID() + "@example.invalid", "hash"));
        grant(chartReader, company, CompanyPermission.ACCOUNT_CLASSIFICATION_VIEW);
    }

    // ---------------------------------------------------------------- chart

    @Test
    void chartWithSnapshotCoverageIsTheFullChartWithRawSourceCodes() throws Exception {
        mvc.perform(chart(source).with(as(chartReader)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountingSourceId").value(source.getId().toString()))
                .andExpect(jsonPath("$.availability").value("FULL_CHART"))
                .andExpect(jsonPath("$.coverage.dataset").value("CHART"))
                .andExpect(jsonPath("$.coverage.publishedAt").value("2026-03-31T12:00:00Z"))
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.items[*].sourceAccountId", contains("1", "10", "30")))
                .andExpect(jsonPath("$.items[1].accountId").value(cash.getId().toString()))
                .andExpect(jsonPath("$.items[1].classificationCode").value("1.1.01.001"))
                .andExpect(jsonPath("$.items[1].description").value("Caixa sintético"))
                .andExpect(jsonPath("$.items[1].sourceCodes.accountType").value("A"))
                .andExpect(jsonPath("$.items[1].sourceCodes.calculationType").value("calc-10"))
                .andExpect(jsonPath("$.items[1].sourceCodes.balanceNature").value("D"))
                .andExpect(jsonPath("$.items[1].sourceCodes.balanceControl").value("ctl-10"))
                .andExpect(jsonPath("$.items[1].sourceCodes.showInBalanceSheet").value("S"))
                .andExpect(jsonPath("$.items[1].sourceCodes.nativeDreCode").hasJsonPath())
                .andExpect(jsonPath("$.items[1].sourceCodes.nativeDreCode").value(nullValue()))
                .andExpect(jsonPath("$.items[1].sourceCodes.nativeDreAccount").value(nullValue()));
    }

    @Test
    void observedAccountsAreNeverPresentedAsFullChart() throws Exception {
        mvc.perform(chart(source).with(as(chartReader)).param("search", "observada"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", empty()));

        mvc.perform(chart(unpublishedSource).with(as(chartReader)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availability").value("IMPORTED_ACCOUNTS_ONLY"))
                .andExpect(jsonPath("$.coverage.publishedAt").hasJsonPath())
                .andExpect(jsonPath("$.coverage.publishedAt").value(nullValue()))
                .andExpect(jsonPath("$.items", empty()))
                .andExpect(jsonPath("$.totalItems").value(0));

        // O universo importado/observado continua no endpoint legado, inalterado.
        mvc.perform(get(IMPORTED_ACCOUNTS, company.getId()).with(as(chartReader))
                        .param("accountingSourceId", source.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.universe").value("IMPORTED_ACCOUNTS"))
                .andExpect(jsonPath("$.totalItems").value(4));
    }

    @Test
    void chartSearchesAndPagesOnTheServer() throws Exception {
        mvc.perform(chart(source).with(as(chartReader)).param("search", "RECEITA"))
                .andExpect(jsonPath("$.items[*].sourceAccountId", contains("30")))
                .andExpect(jsonPath("$.totalItems").value(1));
        mvc.perform(chart(source).with(as(chartReader)).param("search", "1.1.01"))
                .andExpect(jsonPath("$.items[*].sourceAccountId", contains("10")));
        mvc.perform(chart(source).with(as(chartReader)).param("search", "30"))
                .andExpect(jsonPath("$.items[*].sourceAccountId", contains("30")));
        mvc.perform(chart(source).with(as(chartReader)).param("search", "%"))
                .andExpect(jsonPath("$.items", empty()));

        mvc.perform(chart(source).with(as(chartReader)).param("size", "2").param("page", "1"))
                .andExpect(jsonPath("$.items[*].sourceAccountId", contains("30")))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));

        mvc.perform(chart(source).with(as(chartReader)).param("size", "101")).andExpect(status().isBadRequest());
        mvc.perform(chart(source).with(as(chartReader)).param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get(CHART, company.getId()).with(as(chartReader))).andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------ movements

    @Test
    void movementsCarrySynchronizedV2MetadataWithExplicitNulls() throws Exception {
        movement(cash, "E1", AccountingMovementSide.DEBIT, "2026-03-10", "12.3400");
        movement(revenue, "E1", AccountingMovementSide.CREDIT, "2026-03-10", "12.3400");
        store.persistEntrySourceMetadata(source.getId(), new CanonicalEntrySourceMetadata(
                "E1", Instant.parse("2026-03-10T14:15:16Z"), "H7", "OR3", "LN", "DO1", "L42", null,
                "GRP-1", "TX-9", "2"));

        mvc.perform(movementsInMarch().with(as(ledgerReader)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].sourceEntryKey").value("E1"))
                .andExpect(jsonPath("$.items[0].amount").value("12.3400"))
                .andExpect(jsonPath("$.items[0].sourceMetadataStatus").value("SYNCHRONIZED"))
                .andExpect(jsonPath("$.items[0].sourceMetadata.sourceRecordedAt").value("2026-03-10T14:15:16Z"))
                .andExpect(jsonPath("$.items[0].sourceMetadata.historyCode").value("H7"))
                .andExpect(jsonPath("$.items[0].sourceMetadata.originCode").value("OR3"))
                .andExpect(jsonPath("$.items[0].sourceMetadata.entryTypeCode").value("LN"))
                .andExpect(jsonPath("$.items[0].sourceMetadata.dataOriginCode").value("DO1"))
                .andExpect(jsonPath("$.items[0].sourceMetadata.batchCode").value("L42"))
                .andExpect(jsonPath("$.items[0].sourceMetadata.documentNumber").hasJsonPath())
                .andExpect(jsonPath("$.items[0].sourceMetadata.documentNumber").value(nullValue()))
                .andExpect(jsonPath("$.items[0].sourceMetadata.operationGroupKey").value("GRP-1"))
                .andExpect(jsonPath("$.items[0].sourceMetadata.transactionKey").value("TX-9"))
                .andExpect(jsonPath("$.items[0].sourceMetadata.originEstablishmentCode").value("2"))
                .andExpect(jsonPath("$.items[1].sourceMetadataStatus").value("SYNCHRONIZED"));
    }

    @Test
    void movementsWithoutMetadataSayNotSynchronizedAndKeepTheLegacyContract() throws Exception {
        movement(cash, "LEG", AccountingMovementSide.DEBIT, "2026-03-11", "5.0000");

        mvc.perform(movementsInMarch().with(as(ledgerReader)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").exists())
                .andExpect(jsonPath("$.items[0].accountingDate").value("2026-03-11"))
                .andExpect(jsonPath("$.items[0].accountingSourceId").value(source.getId().toString()))
                .andExpect(jsonPath("$.items[0].accountId").value(cash.getId().toString()))
                .andExpect(jsonPath("$.items[0].accountKey").value("1.1.01.001"))
                .andExpect(jsonPath("$.items[0].accountDescription").value("Caixa sintético"))
                .andExpect(jsonPath("$.items[0].side").value("DEBIT"))
                .andExpect(jsonPath("$.items[0].history").value("Synthetic history LEG"))
                .andExpect(jsonPath("$.items[0].sourceMetadataStatus").value("NOT_SYNCHRONIZED"))
                .andExpect(jsonPath("$.items[0].sourceMetadata").hasJsonPath())
                .andExpect(jsonPath("$.items[0].sourceMetadata").value(nullValue()))
                .andExpect(jsonPath("$.totals.debit").value("5.0000"))
                .andExpect(jsonPath("$.notSynchronized",
                        contains("COUNTERPART", "DOCUMENT", "COST_CENTER", "CLOSING_ENTRY", "OPENING_BALANCE")));
    }

    @Test
    void movementMetadataNeverCrossesSources() throws Exception {
        movement(cash, "SAME", AccountingMovementSide.DEBIT, "2026-03-12", "1.0000");
        // Mesma chave de lançamento em outra fonte: não pode ser atribuída ao movimento desta fonte.
        store.persistEntrySourceMetadata(unpublishedSource.getId(), new CanonicalEntrySourceMetadata(
                "SAME", null, "HX", null, null, null, null, "DOC-X", null, null, null));

        mvc.perform(movementsInMarch().with(as(ledgerReader)))
                .andExpect(jsonPath("$.items[0].sourceMetadataStatus").value("NOT_SYNCHRONIZED"))
                .andExpect(jsonPath("$.items[0].sourceMetadata").value(nullValue()));
    }

    // --------------------------------------------------------------- totals

    @Test
    void dailyAndMonthlyTotalsAreSeparateAndExact() throws Exception {
        daily(source, "1", "10", "2026-03-02", "LN", "99999999999.9999", "0.0100");
        daily(source, "1", "30", "2026-03-02", "LN", "0", "12.5");
        monthly(source, "1", "10", "2026-03", "LN", "1000.0001", "2.0000");

        mvc.perform(totals("DAILY", "2026-03-01", "2026-03-31").with(as(ledgerReader)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountingSourceId").value(source.getId().toString()))
                .andExpect(jsonPath("$.granularity").value("DAILY"))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].period").value("2026-03-02"))
                .andExpect(jsonPath("$.items[0].establishmentCode").value("1"))
                .andExpect(jsonPath("$.items[0].sourceAccountId").value("10"))
                .andExpect(jsonPath("$.items[0].accountId").value(cash.getId().toString()))
                .andExpect(jsonPath("$.items[0].entryTypeCode").value("LN"))
                .andExpect(jsonPath("$.items[0].debitTotal").value("99999999999.9999"))
                .andExpect(jsonPath("$.items[0].creditTotal").value("0.0100"))
                .andExpect(jsonPath("$.items[1].debitTotal").value("0.0000"))
                .andExpect(jsonPath("$.items[1].creditTotal").value("12.5000"))
                .andExpect(jsonPath("$.items[0].balance").doesNotExist())
                .andExpect(jsonPath("$.items[0].saldo").doesNotExist());

        mvc.perform(totals("MONTHLY", "2026-03-01", "2026-03-31").with(as(ledgerReader)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.granularity").value("MONTHLY"))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].period").value("2026-03"))
                .andExpect(jsonPath("$.items[0].debitTotal").value("1000.0001"))
                .andExpect(jsonPath("$.items[0].creditTotal").value("2.0000"));
    }

    @Test
    void totalsRespectPeriodEstablishmentAndAccountFilters() throws Exception {
        daily(source, "1", "10", "2026-02-28", "LN", "1", "0");
        daily(source, "1", "10", "2026-03-01", "LN", "2", "0");
        daily(source, "2", "10", "2026-03-01", "LN", "3", "0");
        daily(source, "1", "30", "2026-03-31", "LN", "4", "0");
        daily(source, "1", "10", "2026-04-01", "LN", "5", "0");
        monthly(source, "1", "10", "2026-02", "LN", "6", "0");
        monthly(source, "1", "10", "2026-03", "LN", "7", "0");
        monthly(source, "2", "10", "2026-04", "LN", "8", "0");

        mvc.perform(totals("DAILY", "2026-03-01", "2026-03-31").with(as(ledgerReader)))
                .andExpect(jsonPath("$.items[*].debitTotal", contains("2.0000", "3.0000", "4.0000")));
        mvc.perform(totals("DAILY", "2026-03-01", "2026-03-31").with(as(ledgerReader))
                        .param("establishmentCode", "2"))
                .andExpect(jsonPath("$.items[*].debitTotal", contains("3.0000")))
                .andExpect(jsonPath("$.items[0].establishmentCode").value("2"));
        mvc.perform(totals("DAILY", "2026-03-01", "2026-03-31").with(as(ledgerReader))
                        .param("sourceAccountId", "30"))
                .andExpect(jsonPath("$.items[*].debitTotal", contains("4.0000")));
        mvc.perform(totals("MONTHLY", "2026-03-01", "2026-04-30").with(as(ledgerReader)))
                .andExpect(jsonPath("$.items[*].period", contains("2026-03", "2026-04")));
        mvc.perform(totals("MONTHLY", "2026-02-01", "2026-04-30").with(as(ledgerReader))
                        .param("establishmentCode", "1"))
                .andExpect(jsonPath("$.items[*].debitTotal", contains("6.0000", "7.0000")));

        mvc.perform(totals("DAILY", "2026-03-01", "2026-03-31").with(as(ledgerReader))
                        .param("size", "2").param("page", "1"))
                .andExpect(jsonPath("$.items[*].debitTotal", contains("4.0000")))
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void totalsRejectAmbiguousRequests() throws Exception {
        mvc.perform(totals("MONTHLY", "2026-03-02", "2026-03-31").with(as(ledgerReader)))
                .andExpect(status().isBadRequest());
        mvc.perform(totals("MONTHLY", "2026-03-01", "2026-03-30").with(as(ledgerReader)))
                .andExpect(status().isBadRequest());
        mvc.perform(totals("DAILY", "2026-03-31", "2026-03-01").with(as(ledgerReader)))
                .andExpect(status().isBadRequest());
        mvc.perform(totals("BALANCE", "2026-03-01", "2026-03-31").with(as(ledgerReader)))
                .andExpect(status().isBadRequest());
        mvc.perform(get(TOTALS, company.getId()).with(as(ledgerReader))
                        .param("accountingSourceId", source.getId().toString())
                        .param("startDate", "2026-03-01").param("endDate", "2026-03-31"))
                .andExpect(status().isBadRequest());
        mvc.perform(totals("DAILY", "2026-03-01", "2026-03-31").with(as(ledgerReader)).param("size", "201"))
                .andExpect(status().isBadRequest());
        mvc.perform(totals("DAILY", "2026-03-01", "2026-03-31").with(as(ledgerReader))
                        .param("establishmentCode", " "))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------- coverage

    @Test
    void temporalCoveragePreservesGapsAndEstablishmentScope() throws Exception {
        coverage.recordInterval(source.getId(), AccountingDataset.MOVEMENTS, null,
                LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-31"));
        coverage.recordInterval(source.getId(), AccountingDataset.MOVEMENTS, null,
                LocalDate.parse("2026-03-01"), LocalDate.parse("2026-03-31"));
        coverage.recordInterval(source.getId(), AccountingDataset.DAILY_TOTALS, "2",
                LocalDate.parse("2026-02-01"), LocalDate.parse("2026-02-28"));

        mvc.perform(coverage(source, "MOVEMENTS").with(as(ledgerReader)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountingSourceId").value(source.getId().toString()))
                .andExpect(jsonPath("$.dataset").value("MOVEMENTS"))
                .andExpect(jsonPath("$.kind").value("INTERVALS"))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.establishmentCode").value(nullValue()))
                .andExpect(jsonPath("$.importedFrom").value("2026-01-01"))
                .andExpect(jsonPath("$.importedTo").value("2026-03-31"))
                .andExpect(jsonPath("$.intervals", hasSize(2)))
                .andExpect(jsonPath("$.intervals[0].importedFrom").value("2026-01-01"))
                .andExpect(jsonPath("$.intervals[0].importedTo").value("2026-01-31"))
                .andExpect(jsonPath("$.intervals[1].importedFrom").value("2026-03-01"))
                .andExpect(jsonPath("$.intervals[1].importedTo").value("2026-03-31"))
                .andExpect(jsonPath("$.publishedAt").value(nullValue()));

        mvc.perform(coverage(source, "DAILY_TOTALS").with(as(ledgerReader)))
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.intervals", empty()));
        mvc.perform(coverage(source, "DAILY_TOTALS").with(as(ledgerReader)).param("establishmentCode", "2"))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.establishmentCode").value("2"))
                .andExpect(jsonPath("$.intervals[0].importedTo").value("2026-02-28"));
    }

    @Test
    void snapshotCoverageHasPublicationInstantAndNoArtificialPeriod() throws Exception {
        mvc.perform(coverage(source, "CHART").with(as(chartReader)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("SNAPSHOT"))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.publishedAt").value("2026-03-31T12:00:00Z"))
                .andExpect(jsonPath("$.importedFrom").hasJsonPath())
                .andExpect(jsonPath("$.importedFrom").value(nullValue()))
                .andExpect(jsonPath("$.importedTo").value(nullValue()))
                .andExpect(jsonPath("$.intervals", empty()));

        mvc.perform(coverage(unpublishedSource, "CHART").with(as(chartReader)))
                .andExpect(jsonPath("$.kind").value("SNAPSHOT"))
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.publishedAt").value(nullValue()));

        mvc.perform(coverage(source, "NOT_A_DATASET").with(as(chartReader))).andExpect(status().isBadRequest());
        mvc.perform(get(COVERAGE, company.getId()).with(as(chartReader))
                        .param("accountingSourceId", source.getId().toString()))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------- catalog

    @Test
    void originCatalogIsReturnedRawWhenPublished() throws Exception {
        catalog(source, "2", "Descrição técnica B");
        catalog(source, "10", "Descrição técnica A");
        coverage.recordSnapshot(source.getId(), AccountingDataset.ORIGIN_CATALOG, null, CHART_PUBLISHED_AT);

        mvc.perform(catalog(source).with(as(ledgerReader)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availability").value("AVAILABLE"))
                .andExpect(jsonPath("$.publishedAt").value("2026-03-31T12:00:00Z"))
                .andExpect(jsonPath("$.items[*].originCode", contains("10", "2")))
                .andExpect(jsonPath("$.items[0].description").value("Descrição técnica A"))
                .andExpect(jsonPath("$.items[0].meaning").doesNotExist());
    }

    @Test
    void originCatalogWithoutPublicationIsReportedAbsent() throws Exception {
        // Linhas sem cobertura publicada não contam como catálogo disponível.
        catalog(unpublishedSource, "1", "Sem cobertura");

        mvc.perform(catalog(unpublishedSource).with(as(ledgerReader)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availability").value("NOT_PUBLISHED"))
                .andExpect(jsonPath("$.publishedAt").value(nullValue()))
                .andExpect(jsonPath("$.items", empty()));
        mvc.perform(catalog(source).with(as(ledgerReader)))
                .andExpect(jsonPath("$.availability").value("NOT_PUBLISHED"));
    }

    // ------------------------------------------------- isolation / security

    @Test
    void sourceMustBelongToTheRouteCompany() throws Exception {
        UserAccount both = users.saveAndFlush(new UserAccount("Synthetic V2 Both", UUID.randomUUID() + "@example.invalid", "hash"));
        grants.saveAndFlush(new CompanyAccessGrant(both, company, CompanyAccessRole.CUSTOM, CostCenterScope.ALL,
                Set.of(CompanyPermission.ACCOUNTING_MOVEMENT_VIEW, CompanyPermission.ACCOUNT_CLASSIFICATION_VIEW)));

        mvc.perform(chart(foreignSource).with(as(both))).andExpect(status().isNotFound());
        mvc.perform(totals(foreignSource, "DAILY", "2026-03-01", "2026-03-31").with(as(both)))
                .andExpect(status().isNotFound());
        mvc.perform(coverage(foreignSource, "CHART").with(as(both))).andExpect(status().isNotFound());
        mvc.perform(catalog(foreignSource).with(as(both))).andExpect(status().isNotFound());

        UUID unknown = UUID.randomUUID();
        mvc.perform(get(CHART, company.getId()).param("accountingSourceId", unknown.toString()).with(as(both)))
                .andExpect(status().isNotFound());

        // Movimentos de outra Company nunca entram, nem com o id da fonte estrangeira.
        mvc.perform(movementsInMarch().param("accountingSourceId", foreignSource.getId().toString()).with(as(both)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", empty()));
    }

    @Test
    void companyARouteNeverServesCompanyB() throws Exception {
        for (MockHttpServletRequestBuilder request : new MockHttpServletRequestBuilder[] {
                get(CHART, otherCompany.getId()).param("accountingSourceId", foreignSource.getId().toString()),
                get(TOTALS, otherCompany.getId()).param("accountingSourceId", foreignSource.getId().toString())
                        .param("granularity", "DAILY").param("startDate", "2026-03-01").param("endDate", "2026-03-31"),
                get(COVERAGE, otherCompany.getId()).param("accountingSourceId", foreignSource.getId().toString())
                        .param("dataset", "CHART"),
                get(CATALOG, otherCompany.getId()).param("accountingSourceId", foreignSource.getId().toString())}) {
            mvc.perform(request.with(as(ledgerReader))).andExpect(status().isForbidden());
            mvc.perform(request.with(as(chartReader))).andExpect(status().isForbidden());
        }
    }

    @Test
    void eachEndpointChecksItsPermissionOnTheServer() throws Exception {
        mvc.perform(chart(source)).andExpect(status().isUnauthorized());

        mvc.perform(chart(source).with(as(ledgerReader))).andExpect(status().isForbidden());
        mvc.perform(totals("DAILY", "2026-03-01", "2026-03-31").with(as(chartReader))).andExpect(status().isForbidden());
        mvc.perform(catalog(source).with(as(chartReader))).andExpect(status().isForbidden());
        mvc.perform(coverage(source, "CHART").with(as(ledgerReader))).andExpect(status().isOk());
        mvc.perform(coverage(source, "MOVEMENTS").with(as(chartReader))).andExpect(status().isOk());

        UserAccount manager = users.saveAndFlush(new UserAccount("Synthetic V2 Manager", UUID.randomUUID() + "@example.invalid", "hash"));
        grant(manager, company, CompanyPermission.ACCOUNT_CLASSIFICATION_MANAGE);
        mvc.perform(chart(source).with(as(manager))).andExpect(status().isOk());

        UserAccount dreOnly = users.saveAndFlush(new UserAccount("Synthetic V2 DRE", UUID.randomUUID() + "@example.invalid", "hash"));
        grant(dreOnly, company, CompanyPermission.DRE_VIEW);
        mvc.perform(chart(source).with(as(dreOnly))).andExpect(status().isForbidden());
        mvc.perform(totals("DAILY", "2026-03-01", "2026-03-31").with(as(dreOnly))).andExpect(status().isForbidden());
        mvc.perform(coverage(source, "CHART").with(as(dreOnly))).andExpect(status().isForbidden());
        mvc.perform(catalog(source).with(as(dreOnly))).andExpect(status().isForbidden());

        UserAccount restricted = users.saveAndFlush(new UserAccount("Synthetic V2 Restricted", UUID.randomUUID() + "@example.invalid", "hash"));
        grants.saveAndFlush(new CompanyAccessGrant(restricted, company, CompanyAccessRole.ADMIN, CostCenterScope.RESTRICTED,
                Set.of(CompanyPermission.ACCOUNTING_MOVEMENT_VIEW, CompanyPermission.ACCOUNT_CLASSIFICATION_VIEW)));
        mvc.perform(chart(source).with(as(restricted))).andExpect(status().isForbidden());
        mvc.perform(totals("DAILY", "2026-03-01", "2026-03-31").with(as(restricted))).andExpect(status().isForbidden());
    }

    @Test
    void fileImportManagerCanReadSourcesWithoutQuestorSyncOrOtherAccountingReadPermissions() throws Exception {
        UserAccount importer = users.saveAndFlush(new UserAccount(
                "Synthetic File Import Reader",
                UUID.randomUUID() + "@example.invalid",
                "hash"));
        grant(importer, company, CompanyPermission.ACCOUNTING_IMPORT_MANAGE);

        mvc.perform(get(READABLE_SOURCES, company.getId()).with(as(importer)))
                .andExpect(status().isOk());
    }

    @Test
    void revokedPermissionTakesEffectOnTheNextRequestOfTheSameSession() throws Exception {
        UserAccount actor = users.saveAndFlush(new UserAccount("Synthetic V2 Revoked", UUID.randomUUID() + "@example.invalid", "hash"));
        CompanyAccessGrant access = grants.saveAndFlush(new CompanyAccessGrant(actor, company, CompanyAccessRole.CUSTOM,
                CostCenterScope.ALL,
                Set.of(CompanyPermission.ACCOUNTING_MOVEMENT_VIEW, CompanyPermission.ACCOUNT_CLASSIFICATION_VIEW)));
        RequestPostProcessor session = as(actor);

        mvc.perform(chart(source).with(session)).andExpect(status().isOk());
        mvc.perform(totals("DAILY", "2026-03-01", "2026-03-31").with(session)).andExpect(status().isOk());
        mvc.perform(coverage(source, "CHART").with(session)).andExpect(status().isOk());
        mvc.perform(catalog(source).with(session)).andExpect(status().isOk());

        jdbc.update(
                "DELETE FROM company_access_grant_permissions WHERE company_access_grant_id = ?",
                access.getId());

        mvc.perform(chart(source).with(session)).andExpect(status().isForbidden());
        mvc.perform(totals("DAILY", "2026-03-01", "2026-03-31").with(session)).andExpect(status().isForbidden());
        mvc.perform(coverage(source, "CHART").with(session)).andExpect(status().isForbidden());
        mvc.perform(catalog(source).with(session)).andExpect(status().isForbidden());
    }

    @Test
    void systemAdministratorUsesTheOfficialGlobalAccessPath() throws Exception {
        UserAccount sysadmin = users.saveAndFlush(new UserAccount("Synthetic V2 Sysadmin",
                UUID.randomUUID() + "@example.invalid", "hash", Set.of(PlatformPermission.SYSTEM_ADMIN)));

        mvc.perform(chart(source).with(as(sysadmin))).andExpect(status().isOk());
        mvc.perform(totals("DAILY", "2026-03-01", "2026-03-31").with(as(sysadmin))).andExpect(status().isOk());
        mvc.perform(coverage(source, "CHART").with(as(sysadmin))).andExpect(status().isOk());
        mvc.perform(catalog(source).with(as(sysadmin))).andExpect(status().isOk());
        // Acesso global não afrouxa o vínculo fonte ↔ Company da rota.
        mvc.perform(chart(foreignSource).with(as(sysadmin))).andExpect(status().isNotFound());

        UserAccount platformUserManager = users.saveAndFlush(new UserAccount("Synthetic V2 Platform",
                UUID.randomUUID() + "@example.invalid", "hash", Set.of(PlatformPermission.PLATFORM_USER_MANAGE)));
        mvc.perform(chart(source).with(as(platformUserManager))).andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------- helpers

    private MockHttpServletRequestBuilder chart(AccountingSource target) {
        return get(CHART, company.getId()).param("accountingSourceId", target.getId().toString());
    }

    private MockHttpServletRequestBuilder totals(String granularity, String start, String end) {
        return totals(source, granularity, start, end);
    }

    private MockHttpServletRequestBuilder totals(AccountingSource target, String granularity, String start, String end) {
        return get(TOTALS, company.getId()).param("accountingSourceId", target.getId().toString())
                .param("granularity", granularity).param("startDate", start).param("endDate", end);
    }

    private MockHttpServletRequestBuilder coverage(AccountingSource target, String dataset) {
        return get(COVERAGE, company.getId()).param("accountingSourceId", target.getId().toString())
                .param("dataset", dataset);
    }

    private MockHttpServletRequestBuilder catalog(AccountingSource target) {
        return get(CATALOG, company.getId()).param("accountingSourceId", target.getId().toString());
    }

    private MockHttpServletRequestBuilder movementsInMarch() {
        return get(MOVEMENTS, company.getId()).param("startDate", "2026-03-01").param("endDate", "2026-03-31");
    }

    private AccountingAccount chartAccount(
            AccountingSource target, String sourceAccountId, String classification, String description, String type) {
        AccountingAccount account = accounts.saveAndFlush(
                new AccountingAccount(target, sourceAccountId, classification, description));
        store.persistAccountSourceAttributes(target.getId(), new CanonicalAccountingAccount(
                sourceAccountId, classification, description,
                new SourceAccountCodes(type, "calc-" + sourceAccountId, "D", "ctl-" + sourceAccountId, "S", null, null)));
        return account;
    }

    private void movement(AccountingAccount account, String entry, AccountingMovementSide side, String date, String amount) {
        movements.saveAndFlush(new AccountingMovement(account, entry, side, LocalDate.parse(date), new BigDecimal(amount),
                "Synthetic history " + entry));
    }

    private void daily(AccountingSource target, String establishment, String account, String day, String type,
                       String debit, String credit) {
        store.persistDailyTotals(target.getId(), new CanonicalAccountDailyDebitCreditTotals(
                establishment, account, LocalDate.parse(day), type, new BigDecimal(debit), new BigDecimal(credit)));
    }

    private void monthly(AccountingSource target, String establishment, String account, String month, String type,
                         String debit, String credit) {
        store.persistMonthlyTotals(target.getId(), new CanonicalAccountMonthlyDebitCreditTotals(
                establishment, account, YearMonth.parse(month), type, new BigDecimal(debit), new BigDecimal(credit)));
    }

    private void catalog(AccountingSource target, String code, String description) {
        store.persistOriginCatalogEntry(target.getId(), new CanonicalOriginCatalogEntry(code, description));
    }

    private void grant(UserAccount account, Company target, CompanyPermission permission) {
        grants.saveAndFlush(new CompanyAccessGrant(account, target, CompanyAccessRole.CUSTOM, CostCenterScope.ALL,
                Set.of(permission)));
    }

    private static RequestPostProcessor as(UserAccount account) {
        return user(new UserAccountPrincipal(account));
    }
}
