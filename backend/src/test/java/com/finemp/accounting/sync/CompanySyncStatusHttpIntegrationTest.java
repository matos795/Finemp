package com.finemp.accounting.sync;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.finemp.TestcontainersConfiguration;
import com.finemp.access.AccountingFirmAccessGrant;
import com.finemp.access.AccountingFirmAccessGrantRepository;
import com.finemp.access.AccountingFirmPermission;
import com.finemp.access.CompanyAccessGrant;
import com.finemp.access.CompanyAccessGrantRepository;
import com.finemp.access.CompanyAccessRole;
import com.finemp.access.CompanyPermission;
import com.finemp.access.CostCenterScope;
import com.finemp.access.PlatformPermission;
import com.finemp.access.UserAccount;
import com.finemp.access.UserAccountRepository;
import com.finemp.accounting.source.AccountingSource;
import com.finemp.accounting.source.AccountingSourceRepository;
import com.finemp.accounting.source.AccountingSourceType;
import com.finemp.accountingfirm.AccountingFirm;
import com.finemp.accountingfirm.AccountingFirmRepository;
import com.finemp.client.Client;
import com.finemp.client.ClientRepository;
import com.finemp.company.Company;
import com.finemp.company.CompanyRepository;
import com.finemp.security.UserAccountPrincipal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * M3 (#440): estado de sincronização por Company para o analista operacional.
 * Leitura de AccountingSource + SyncRun; autorização = QUESTOR_SYNC na Company,
 * a mesma exigida pela API de SyncRun. Sem permissão nova nem bypass de contabilidade.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class CompanySyncStatusHttpIntegrationTest {

    private static final String ROUTE = "/api/companies/{companyId}/accounting-sources";
    private static final Instant T0 = Instant.parse("2026-11-01T10:00:00Z");

    @Autowired private MockMvc mvc;
    @Autowired private UserAccountRepository users;
    @Autowired private AccountingFirmRepository firms;
    @Autowired private ClientRepository clients;
    @Autowired private CompanyRepository companies;
    @Autowired private AccountingSourceRepository sources;
    @Autowired private CompanyAccessGrantRepository grants;
    @Autowired private AccountingFirmAccessGrantRepository firmGrants;
    @Autowired private SyncRunRepository runs;

    private AccountingFirm firm;
    private Company company;
    private Company otherCompany;
    private UserAccount analyst;

    @BeforeEach
    void prepare() {
        firm = firms.saveAndFlush(new AccountingFirm("Synthetic Sync Firm", true));
        Client client = clients.saveAndFlush(new Client(firm, "Synthetic Sync Client", true));
        company = companies.saveAndFlush(new Company(client, "Synthetic Sync Company", "31313131000131"));
        otherCompany = companies.saveAndFlush(new Company(client, "Synthetic Other Company", "32323232000132"));
        analyst = account("Synthetic Sync Analyst", Set.of());
        grant(analyst, company, Set.of(CompanyPermission.QUESTOR_SYNC));
    }

    @Test
    void requiresSessionAndQuestorSyncOnThatCompany() throws Exception {
        mvc.perform(get(ROUTE, company.getId())).andExpect(status().isUnauthorized());

        // Grant em outra Company não vale para esta.
        mvc.perform(get(ROUTE, otherCompany.getId()).with(as(analyst))).andExpect(status().isForbidden());

        // Outras permissões de dados da Company não substituem QUESTOR_SYNC.
        UserAccount reader = account("Synthetic Ledger Reader", Set.of());
        grant(reader, company, Set.of(CompanyPermission.ACCOUNTING_MOVEMENT_VIEW, CompanyPermission.DRE_VIEW));
        mvc.perform(get(ROUTE, company.getId()).with(as(reader))).andExpect(status().isForbidden());

        // Permissões da contabilidade (inclusive de gestão) não leem estado de dados da Company.
        UserAccount navigator = account("Synthetic Navigator", Set.of());
        firmGrants.saveAndFlush(new AccountingFirmAccessGrant(navigator, firm, Set.of(
                AccountingFirmPermission.HIERARCHY_VIEW, AccountingFirmPermission.HIERARCHY_MANAGE,
                AccountingFirmPermission.ACCOUNTING_SOURCE_VIEW, AccountingFirmPermission.ACCOUNTING_SOURCE_MANAGE)));
        mvc.perform(get(ROUTE, company.getId()).with(as(navigator))).andExpect(status().isForbidden());

        // Permissão de plataforma não administrativa não é bypass.
        UserAccount platformUser = account("Synthetic Platform User", Set.of(PlatformPermission.PLATFORM_USER_MANAGE));
        mvc.perform(get(ROUTE, company.getId()).with(as(platformUser))).andExpect(status().isForbidden());
    }

    @Test
    void listsOnlyThisCompanySourcesWithoutExternalConfiguration() throws Exception {
        AccountingSource source = sources.saveAndFlush(
                new AccountingSource(company, AccountingSourceType.QUESTOR, "synthetic-external-ref", true));
        sources.saveAndFlush(new AccountingSource(otherCompany, AccountingSourceType.QUESTOR, "other-ref", true));

        mvc.perform(get(ROUTE, company.getId()).with(as(analyst)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].accountingSourceId").value(source.getId().toString()))
                .andExpect(jsonPath("$.items[0].type").value("QUESTOR"))
                .andExpect(jsonPath("$.items[0].active").value(true))
                .andExpect(jsonPath("$.items[0].externalReference").doesNotExist())
                // Sem execução: não inventa cobertura.
                .andExpect(jsonPath("$.items[0].latestRun").isEmpty())
                .andExpect(jsonPath("$.items[0].latestSucceededRun").isEmpty());
    }

    @Test
    void reportsLatestRunAndLatestSucceededCoverageSeparately() throws Exception {
        AccountingSource source = sources.saveAndFlush(
                new AccountingSource(company, AccountingSourceType.QUESTOR, "synthetic-ref", true));

        SyncRun older = succeeded(source, LocalDate.parse("2026-01-01"), LocalDate.parse("2026-06-30"), T0, 10);
        SyncRun covered = succeeded(source, LocalDate.parse("2026-01-01"), LocalDate.parse("2026-09-30"),
                T0.plusSeconds(3_600), 25);
        SyncRun failed = run(source, LocalDate.parse("2026-01-01"), LocalDate.parse("2026-10-31"), T0.plusSeconds(7_200));
        failed.start(T0.plusSeconds(7_260));
        failed.fail(SyncFailureCode.values()[0], T0.plusSeconds(7_320));
        runs.saveAndFlush(failed);

        mvc.perform(get(ROUTE, company.getId()).with(as(analyst)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].latestRun.id").value(failed.getId().toString()))
                .andExpect(jsonPath("$.items[0].latestRun.status").value("FAILED"))
                .andExpect(jsonPath("$.items[0].latestRun.failureCode").value(SyncFailureCode.values()[0].name()))
                .andExpect(jsonPath("$.items[0].latestSucceededRun.id").value(covered.getId().toString()))
                .andExpect(jsonPath("$.items[0].latestSucceededRun.startDate").value("2026-01-01"))
                .andExpect(jsonPath("$.items[0].latestSucceededRun.endDate").value("2026-09-30"))
                .andExpect(jsonPath("$.items[0].latestSucceededRun.importedCount").value(25));
        // A execução mais antiga não é escolhida.
        org.assertj.core.api.Assertions.assertThat(older.getId()).isNotEqualTo(covered.getId());
    }

    @Test
    void revokedGrantIsDeniedInTheSameSession() throws Exception {
        var principal = as(analyst);
        mvc.perform(get(ROUTE, company.getId()).with(principal)).andExpect(status().isOk());

        grants.deleteAll(grants.findAllByUserAccountId(analyst.getId()));
        grants.flush();

        mvc.perform(get(ROUTE, company.getId()).with(principal)).andExpect(status().isForbidden());
    }

    @Test
    void systemAdminKeepsGlobalCompanyAccess() throws Exception {
        UserAccount admin = account("Synthetic System Admin", Set.of(PlatformPermission.SYSTEM_ADMIN));
        sources.saveAndFlush(new AccountingSource(company, AccountingSourceType.QUESTOR, "admin-ref", true));

        mvc.perform(get(ROUTE, company.getId()).with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)));
    }

    private SyncRun succeeded(AccountingSource source, LocalDate start, LocalDate end, Instant at, long imported) {
        SyncRun run = run(source, start, end, at);
        run.start(at.plusSeconds(60));
        run.succeed(imported, imported, 0, at.plusSeconds(120));
        return runs.saveAndFlush(run);
    }

    private SyncRun run(AccountingSource source, LocalDate start, LocalDate end, Instant at) {
        return runs.saveAndFlush(new SyncRun(source, analyst.getId(), UUID.randomUUID(), SyncRunMode.INITIAL, start, end, at));
    }

    private UserAccount account(String name, Set<PlatformPermission> platform) {
        return users.saveAndFlush(new UserAccount(name, UUID.randomUUID() + "@example.invalid", "hash", platform));
    }

    private void grant(UserAccount account, Company target, Set<CompanyPermission> permissions) {
        grants.saveAndFlush(new CompanyAccessGrant(account, target, CompanyAccessRole.CUSTOM, CostCenterScope.ALL, permissions));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor as(UserAccount account) {
        return user(new UserAccountPrincipal(account));
    }
}
