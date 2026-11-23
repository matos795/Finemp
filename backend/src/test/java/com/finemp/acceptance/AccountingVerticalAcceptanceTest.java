package com.finemp.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import com.finemp.accounting.classification.DreClassification;
import com.finemp.accounting.classification.DreClassificationRepository;
import com.finemp.accounting.ingestion.ImportedAccountingMovement;
import com.finemp.accounting.movement.AccountingMovementSide;
import com.finemp.accounting.reading.AccountingCompanyIdentityReader;
import com.finemp.accounting.reading.AccountingSourceReaderRegistry;
import com.finemp.accounting.reading.ExternalCompanyIdentity;
import com.finemp.accounting.source.AccountingSource;
import com.finemp.accounting.source.AccountingSourceRepository;
import com.finemp.accounting.source.AccountingSourceType;
import com.finemp.accounting.sync.execution.SyncExecutionOutcome;
import com.finemp.accounting.sync.execution.SyncRunExecutor;
import com.finemp.accountingfirm.AccountingFirm;
import com.finemp.accountingfirm.AccountingFirmRepository;
import com.finemp.client.Client;
import com.finemp.client.ClientRepository;
import com.finemp.company.Company;
import com.finemp.company.CompanyRepository;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * M7 (#448): aceite vertical do MVP contábil, ponta a ponta sobre PostgreSQL real.
 *
 * <p>login → carteira → empresa → fonte → sincronização (executor e publicação reais)
 * → plano importado/classificação → movimentos → DRE → logout. Só a fronteira externa
 * (leitura Questor/Firebird) é simulada. Não é {@code @Transactional}: o controle de
 * SyncRun exige transações reais; o teste remove apenas as próprias linhas sintéticas.
 */
@SpringBootTest(properties = {
        "finemp.sync.control.enabled=true",
        "finemp.sync.executor.enabled=true",
        "finemp.sync.retry.max-attempts=1"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AccountingVerticalAcceptanceTest {

    private static final String PASSWORD = "senha-sintetica-segura";
    private static final LocalDate START = LocalDate.of(2026, 9, 1);
    private static final LocalDate END = LocalDate.of(2026, 9, 30);

    @Autowired private MockMvc mvc;
    @Autowired private UserAccountRepository users;
    @Autowired private AccountingFirmRepository firms;
    @Autowired private ClientRepository clients;
    @Autowired private CompanyRepository companies;
    @Autowired private AccountingSourceRepository sources;
    @Autowired private CompanyAccessGrantRepository grants;
    @Autowired private DreClassificationRepository catalog;
    @Autowired private SyncRunExecutor executor;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private AccountingCompanyIdentityReader identities;
    @MockitoBean private AccountingSourceReaderRegistry readers;

    private final List<UUID> userIds = new ArrayList<>();
    private AccountingFirm firm;
    private AccountingFirm otherFirm;
    private Client client;
    private Client otherClient;
    private Company company;
    private Company forbiddenCompany;
    private Company otherFirmCompany;
    private AccountingSource source;
    private DreClassification revenueLine;
    private UserAccount analyst;
    private UserAccount admin;

    @BeforeEach
    void prepare() {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        firm = firms.saveAndFlush(new AccountingFirm("Synthetic Acceptance Firm " + tag, true));
        otherFirm = firms.saveAndFlush(new AccountingFirm("Synthetic Isolated Firm " + tag, true));
        client = clients.saveAndFlush(new Client(firm, "Synthetic Acceptance Client", true));
        otherClient = clients.saveAndFlush(new Client(otherFirm, "Synthetic Isolated Client", true));
        company = companies.saveAndFlush(new Company(client, "Synthetic Acceptance Company", "71717171000171"));
        forbiddenCompany = companies.saveAndFlush(new Company(client, "Synthetic Forbidden Company", "72727272000172"));
        otherFirmCompany = companies.saveAndFlush(new Company(otherClient, "Synthetic Isolated Company", "73737373000173"));
        source = sources.saveAndFlush(new AccountingSource(company, AccountingSourceType.QUESTOR, "synthetic", true));
        revenueLine = catalog.saveAndFlush(
                new DreClassification("Synthetic Acceptance " + tag, "Receita bruta", "Serviços"));

        analyst = user("analyst-" + tag, Set.of());
        admin = user("admin-" + tag, Set.of(PlatformPermission.SYSTEM_ADMIN));
        grants.saveAndFlush(new CompanyAccessGrant(analyst, company, CompanyAccessRole.CUSTOM, CostCenterScope.ALL, Set.of(
                CompanyPermission.QUESTOR_SYNC,
                CompanyPermission.ACCOUNT_CLASSIFICATION_MANAGE,
                CompanyPermission.ACCOUNTING_MOVEMENT_VIEW,
                CompanyPermission.DRE_VIEW)));

        when(identities.readIdentity(any())).thenReturn(new ExternalCompanyIdentity("synthetic", "71.717.171/0001-71"));
        when(readers.readMovements(any())).thenReturn(List.of(
                row("J1", "100", "1.1.01.001", "Caixa sintético", AccountingMovementSide.DEBIT, "1250.5000"),
                row("J1", "300", "3.1.01.001", "Receita sintética", AccountingMovementSide.CREDIT, "1250.5000")));
    }

    @AfterEach
    void removeOnlyOwnSyntheticRows() {
        for (UUID sourceId : List.of(source.getId())) {
            jdbc.update("DELETE FROM sync_attempts WHERE sync_run_id IN (SELECT id FROM sync_runs WHERE accounting_source_id = ?)", sourceId);
            jdbc.update("DELETE FROM sync_run_events WHERE sync_run_id IN (SELECT id FROM sync_runs WHERE accounting_source_id = ?)", sourceId);
            jdbc.update("DELETE FROM sync_runs WHERE accounting_source_id = ?", sourceId);
            jdbc.update("DELETE FROM accounting_movements WHERE accounting_source_id = ?", sourceId);
            jdbc.update("DELETE FROM accounting_account_classifications WHERE accounting_account_id IN "
                    + "(SELECT id FROM accounting_accounts WHERE accounting_source_id = ?)", sourceId);
            jdbc.update("DELETE FROM accounting_accounts WHERE accounting_source_id = ?", sourceId);
            jdbc.update("DELETE FROM accounting_sources WHERE id = ?", sourceId);
        }
        jdbc.update("DELETE FROM dre_classifications WHERE id = ?", revenueLine.getId());
        for (UUID userId : userIds) {
            jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id IN "
                    + "(SELECT id FROM company_access_grants WHERE user_account_id = ?)", userId);
            jdbc.update("DELETE FROM company_access_grants WHERE user_account_id = ?", userId);
            jdbc.update("DELETE FROM user_account_platform_permissions WHERE user_account_id = ?", userId);
            jdbc.update("DELETE FROM user_accounts WHERE id = ?", userId);
        }
        for (Company each : List.of(company, forbiddenCompany, otherFirmCompany)) {
            jdbc.update("DELETE FROM companies WHERE id = ?", each.getId());
        }
        for (Client each : List.of(client, otherClient)) {
            jdbc.update("DELETE FROM clients WHERE id = ?", each.getId());
        }
        for (AccountingFirm each : List.of(firm, otherFirm)) {
            jdbc.update("DELETE FROM accounting_firms WHERE id = ?", each.getId());
        }
    }

    @Test
    void restrictedAnalystCompletesTheMvpFlowAndLosesAccessOnRevocation() throws Exception {
        MockHttpSession session = login(analyst);
        String companyId = company.getId().toString();

        // Carteira: só a Company com grant e seus ancestrais; nada da outra contabilidade.
        mvc.perform(get("/api/auth/accounting-portfolio").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountingFirms", hasSize(1)))
                .andExpect(jsonPath("$.accountingFirms[0].accountingFirmId").value(firm.getId().toString()))
                .andExpect(jsonPath("$.accountingFirms[0].scope").value("GRANTED_COMPANIES"))
                .andExpect(jsonPath("$.accountingFirms[0].clients[0].companies", hasSize(1)))
                .andExpect(jsonPath("$.accountingFirms[0].clients[0].companies[0].companyId").value(companyId));

        // Fonte sem execução: sem cobertura inventada.
        mvc.perform(get("/api/companies/{id}/accounting-sources", companyId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].accountingSourceId").value(source.getId().toString()))
                .andExpect(jsonPath("$.items[0].latestRun").isEmpty());

        // Solicitação idempotente pela sessão; executor e publicação reais.
        UUID key = UUID.randomUUID();
        UUID runId = requestSync(session, key);
        assertThat(requestSync(session, key)).isEqualTo(runId);
        assertThat(executor.execute(runId)).isEqualTo(SyncExecutionOutcome.PUBLISHED);

        mvc.perform(get("/api/companies/{id}/accounting-sources", companyId).session(session))
                .andExpect(jsonPath("$.items[0].latestSucceededRun.id").value(runId.toString()))
                .andExpect(jsonPath("$.items[0].latestSucceededRun.importedCount").value(2));

        // Plano importado: duas contas pendentes, universo explícito.
        String accounts = mvc.perform(get("/api/companies/{id}/accounting-accounts", companyId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.universe").value("IMPORTED_ACCOUNTS"))
                .andExpect(jsonPath("$.summary.pending").value(2))
                .andReturn().getResponse().getContentAsString();
        List<String> revenueIds = JsonPath.read(accounts, "$.items[?(@.accountKey == '3.1.01.001')].accountId");

        // Classificação da conta de receita.
        mvc.perform(put("/api/companies/{id}/accounting-accounts/{accountId}/classification", companyId, revenueIds.getFirst())
                        .session(session).with(csrf()).contentType(APPLICATION_JSON)
                        .content("{\"classificationId\":\"" + revenueLine.getId() + "\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/companies/{id}/accounting-accounts", companyId).session(session).param("status", "PENDING"))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.summary.classified").value(1));

        // Movimentos: valores exatos.
        mvc.perform(search(companyId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.totals.debit").value("1250.5000"))
                .andExpect(jsonPath("$.totals.credit").value("1250.5000"));

        // DRE: receita classificada, caixa pendente, cobertura factual.
        assertDre(session, companyId);

        // Reexecução idempotente: nada duplica.
        UUID replay = requestSync(session, UUID.randomUUID());
        assertThat(executor.execute(replay)).isEqualTo(SyncExecutionOutcome.PUBLISHED);
        mvc.perform(search(companyId).session(session)).andExpect(jsonPath("$.items", hasSize(2)));
        assertDre(session, companyId);

        // Falha de leitura: execução FAILED sanitizada; dados e cobertura preservados.
        when(readers.readMovements(any())).thenThrow(new IllegalStateException("synthetic private endpoint detail"));
        UUID failed = requestSync(session, UUID.randomUUID());
        assertThat(executor.execute(failed)).isEqualTo(SyncExecutionOutcome.FAILED);
        mvc.perform(get("/api/companies/{id}/accounting-sources", companyId).session(session))
                .andExpect(jsonPath("$.items[0].latestRun.status").value("FAILED"))
                .andExpect(jsonPath("$.items[0].latestRun.failureCode").value("READ_FAILED"))
                .andExpect(jsonPath("$.items[0].latestSucceededRun.id").value(replay.toString()));
        assertDre(session, companyId);

        // Company proibida e outra contabilidade: 403 em todas as leituras de dados.
        for (Company denied : List.of(forbiddenCompany, otherFirmCompany)) {
            String id = denied.getId().toString();
            mvc.perform(get("/api/companies/{id}/accounting-sources", id).session(session)).andExpect(status().isForbidden());
            mvc.perform(get("/api/companies/{id}/accounting-accounts", id).session(session)).andExpect(status().isForbidden());
            mvc.perform(search(id).session(session)).andExpect(status().isForbidden());
            mvc.perform(dre(id).session(session)).andExpect(status().isForbidden());
        }

        // Revogação vale na mesma sessão, para carteira e dados.
        jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id IN "
                + "(SELECT id FROM company_access_grants WHERE user_account_id = ?)", analyst.getId());
        jdbc.update("DELETE FROM company_access_grants WHERE user_account_id = ?", analyst.getId());
        mvc.perform(dre(companyId).session(session)).andExpect(status().isForbidden());
        mvc.perform(search(companyId).session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/auth/accounting-portfolio").session(session))
                .andExpect(jsonPath("$.accountingFirms", hasSize(0)));

        // Logout encerra a sessão.
        mvc.perform(post("/api/auth/logout").session(session).with(csrf())).andExpect(status().isNoContent());
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void systemAdminNavigatesEveryFirmAndReadsDataWithoutFirmGrant() throws Exception {
        MockHttpSession session = login(admin);
        String portfolio = mvc.perform(get("/api/auth/accounting-portfolio").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> firmIds = JsonPath.read(portfolio, "$.accountingFirms[*].accountingFirmId");
        assertThat(firmIds).contains(firm.getId().toString(), otherFirm.getId().toString());

        mvc.perform(dre(company.getId().toString()).session(session)).andExpect(status().isOk());
        mvc.perform(get("/api/companies/{id}/accounting-sources", otherFirmCompany.getId()).session(session))
                .andExpect(status().isOk());
    }

    private void assertDre(MockHttpSession session, String companyId) throws Exception {
        mvc.perform(dre(companyId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines", hasSize(1)))
                .andExpect(jsonPath("$.groups[0].total").value("1250.5000"))
                .andExpect(jsonPath("$.classifiedTotal").value("1250.5000"))
                .andExpect(jsonPath("$.unclassifiedAccountCount").value(1))
                .andExpect(jsonPath("$.coverage[0].syncedStartDate").value(START.toString()))
                .andExpect(jsonPath("$.coverage[0].syncedEndDate").value(END.toString()));
    }

    private UUID requestSync(MockHttpSession session, UUID key) throws Exception {
        String body = mvc.perform(post("/api/accounting-sources/{id}/sync-runs", source.getId())
                        .session(session).with(csrf()).header("Idempotency-Key", key).contentType(APPLICATION_JSON)
                        .content("{\"mode\":\"INITIAL\",\"startDate\":\"%s\",\"endDate\":\"%s\"}".formatted(START, END)))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder search(String companyId) {
        return get("/api/companies/{id}/accounting-movements/search", companyId)
                .param("startDate", START.toString()).param("endDate", END.toString());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder dre(String companyId) {
        return get("/api/companies/{id}/dre", companyId)
                .param("startDate", START.toString()).param("endDate", END.toString());
    }

    private MockHttpSession login(UserAccount account) throws Exception {
        var result = mvc.perform(post("/api/auth/login").with(csrf()).contentType(APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(account.getEmail(), PASSWORD)))
                .andExpect(status().isNoContent()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private UserAccount user(String tag, Set<PlatformPermission> platform) {
        UserAccount account = users.saveAndFlush(new UserAccount("Synthetic " + tag, tag + "@example.invalid",
                passwordEncoder.encode(PASSWORD), platform));
        userIds.add(account.getId());
        return account;
    }

    private static ImportedAccountingMovement row(String key, String accountId, String accountKey, String description,
                                                  AccountingMovementSide side, String amount) {
        return new ImportedAccountingMovement(key, START.plusDays(9), accountId, accountKey, description, side,
                new BigDecimal(amount), "Synthetic acceptance history");
    }
}
