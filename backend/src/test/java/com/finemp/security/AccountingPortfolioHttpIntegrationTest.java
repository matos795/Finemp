package com.finemp.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.finemp.accountingfirm.AccountingFirm;
import com.finemp.accountingfirm.AccountingFirmRepository;
import com.finemp.client.Client;
import com.finemp.client.ClientRepository;
import com.finemp.company.Company;
import com.finemp.company.CompanyRepository;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carteira operacional da Área da Contabilidade: cada usuário recebe somente
 * a hierarquia que pode navegar. Navegar não autoriza ler dados da empresa.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AccountingPortfolioHttpIntegrationTest {

    private static final String PORTFOLIO = "/api/auth/accounting-portfolio";
    private static final AtomicInteger CNPJ = new AtomicInteger(10_000_000);

    @Autowired private MockMvc mockMvc;
    @Autowired private UserAccountRepository users;
    @Autowired private AccountingFirmRepository firms;
    @Autowired private ClientRepository clients;
    @Autowired private CompanyRepository companies;
    @Autowired private AccountingFirmAccessGrantRepository firmGrants;
    @Autowired private CompanyAccessGrantRepository companyGrants;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get(PORTFOLIO)).andExpect(status().isUnauthorized());
    }

    // 1 + 2 + 3
    @Test
    void restrictedUserSeesOnlyGrantedCompanyAndItsAncestors() throws Exception {
        UserAccount analyst = user("analyst-only-a@example.invalid", Set.of());
        AccountingFirm firm = firm("Synthetic Firm A");
        AccountingFirm otherFirm = firm("Synthetic Firm Without Grant");
        Client client = client(firm, "Synthetic Client X");
        Client emptyClient = client(firm, "Synthetic Client Without Grant");
        Company companyA = company(client, "Synthetic Company A");
        Company companyB = company(client, "Synthetic Company B");
        company(emptyClient, "Synthetic Company C");
        company(client(otherFirm, "Synthetic Client Other"), "Synthetic Company D");
        companyGrant(analyst, companyA, Set.of());

        mockMvc.perform(get(PORTFOLIO).session(login(analyst)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountingFirms.length()").value(1))
                .andExpect(jsonPath("$.accountingFirms[0].accountingFirmId").value(firm.getId().toString()))
                .andExpect(jsonPath("$.accountingFirms[0].scope").value("GRANTED_COMPANIES"))
                .andExpect(jsonPath("$.accountingFirms[0].clients.length()").value(1))
                .andExpect(jsonPath("$.accountingFirms[0].clients[0].clientId").value(client.getId().toString()))
                .andExpect(jsonPath("$.accountingFirms[0].clients[0].companies.length()").value(1))
                .andExpect(jsonPath("$.accountingFirms[0].clients[0].companies[0].companyId")
                        .value(companyA.getId().toString()))
                .andExpect(jsonPath("$..companyId", not(hasItem(companyB.getId().toString()))))
                .andExpect(jsonPath("$..clientId", not(hasItem(emptyClient.getId().toString()))))
                .andExpect(jsonPath("$..accountingFirmId", not(hasItem(otherFirm.getId().toString()))));
    }

    // 3: vínculo de contabilidade sem permissão de hierarquia não revela a contabilidade
    @Test
    void firmMembershipWithoutHierarchyPermissionAndWithoutCompanyGrantShowsNothing() throws Exception {
        UserAccount member = user("member-no-hierarchy@example.invalid", Set.of());
        AccountingFirm firm = firm("Synthetic Firm Member Only");
        company(client(firm, "Synthetic Client"), "Synthetic Company");
        firmGrant(member, firm, Set.of(AccountingFirmPermission.CONNECTOR_MANAGE));

        mockMvc.perform(get(PORTFOLIO).session(login(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountingFirms.length()").value(0));
    }

    // 4
    @Test
    void grantsInDifferentClientsShowOnlyThoseCompanies() throws Exception {
        UserAccount analyst = user("analyst-two-clients@example.invalid", Set.of());
        AccountingFirm firm = firm("Synthetic Firm Two Clients");
        Client first = client(firm, "Synthetic Client 1");
        Client second = client(firm, "Synthetic Client 2");
        Company granted1 = company(first, "Synthetic Granted 1");
        Company hidden1 = company(first, "Synthetic Hidden 1");
        Company granted2 = company(second, "Synthetic Granted 2");
        Company hidden2 = company(second, "Synthetic Hidden 2");
        companyGrant(analyst, granted1, Set.of(CompanyPermission.DRE_VIEW));
        companyGrant(analyst, granted2, Set.of());

        mockMvc.perform(get(PORTFOLIO).session(login(analyst)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountingFirms.length()").value(1))
                .andExpect(jsonPath("$.accountingFirms[0].clients.length()").value(2))
                .andExpect(jsonPath("$.accountingFirms[0].clients[0].name").value("Synthetic Client 1"))
                .andExpect(jsonPath("$.accountingFirms[0].clients[0].companies.length()").value(1))
                .andExpect(jsonPath("$.accountingFirms[0].clients[0].companies[0].companyId").value(granted1.getId().toString()))
                .andExpect(jsonPath("$.accountingFirms[0].clients[1].companies.length()").value(1))
                .andExpect(jsonPath("$.accountingFirms[0].clients[1].companies[0].companyId").value(granted2.getId().toString()))
                .andExpect(jsonPath("$..companyId", not(hasItem(hidden1.getId().toString()))))
                .andExpect(jsonPath("$..companyId", not(hasItem(hidden2.getId().toString()))));
    }

    // 5 + 11
    @Test
    void hierarchyViewSeesWholeFirmButNotOtherFirms() throws Exception {
        UserAccount supervisor = user("supervisor-view@example.invalid", Set.of());
        AccountingFirm firm = firm("Synthetic Supervised Firm");
        AccountingFirm otherFirm = firm("Synthetic Isolated Firm");
        Client client = client(firm, "Synthetic Client With Companies");
        Client emptyClient = client(firm, "Synthetic Client Empty");
        Company first = company(client, "Synthetic First");
        Company second = company(client, "Synthetic Second");
        Company isolated = company(client(otherFirm, "Synthetic Other Client"), "Synthetic Isolated");
        firmGrant(supervisor, firm, Set.of(AccountingFirmPermission.HIERARCHY_VIEW));

        MockHttpSession session = login(supervisor);
        mockMvc.perform(get(PORTFOLIO).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountingFirms.length()").value(1))
                .andExpect(jsonPath("$.accountingFirms[0].scope").value("FULL_HIERARCHY"))
                .andExpect(jsonPath("$.accountingFirms[0].clients.length()").value(2))
                .andExpect(jsonPath("$..clientId", hasItem(emptyClient.getId().toString())))
                .andExpect(jsonPath("$..companyId", hasItem(first.getId().toString())))
                .andExpect(jsonPath("$..companyId", hasItem(second.getId().toString())))
                .andExpect(jsonPath("$..companyId", not(hasItem(isolated.getId().toString()))));

        mockMvc.perform(get("/api/accounting-firms/{firmId}/clients", otherFirm.getId()).session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void hierarchyManageAlsoGivesFullNavigation() throws Exception {
        UserAccount administrator = user("hierarchy-manage-nav@example.invalid", Set.of());
        AccountingFirm firm = firm("Synthetic Managed Firm");
        Company company = company(client(firm, "Synthetic Client"), "Synthetic Company");
        firmGrant(administrator, firm, Set.of(AccountingFirmPermission.HIERARCHY_MANAGE));

        mockMvc.perform(get(PORTFOLIO).session(login(administrator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountingFirms[0].scope").value("FULL_HIERARCHY"))
                .andExpect(jsonPath("$..companyId", hasItem(company.getId().toString())));
    }

    // 10
    @Test
    void revokedGrantDisappearsInTheSameSession() throws Exception {
        UserAccount analyst = user("analyst-revoked@example.invalid", Set.of());
        AccountingFirm firm = firm("Synthetic Firm Revocation");
        Company company = company(client(firm, "Synthetic Client"), "Synthetic Company");
        CompanyAccessGrant grant = companyGrant(analyst, company, Set.of(CompanyPermission.DRE_VIEW));
        MockHttpSession session = login(analyst);

        mockMvc.perform(get(PORTFOLIO).session(session))
                .andExpect(jsonPath("$..companyId", hasItem(company.getId().toString())));

        companyGrants.delete(grant);
        companyGrants.flush();

        mockMvc.perform(get(PORTFOLIO).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountingFirms.length()").value(0));
    }

    // 12
    @Test
    void systemAdminSeesEveryHierarchyWithoutFirmGrant() throws Exception {
        UserAccount admin = user("system-admin-portfolio@example.invalid", Set.of(PlatformPermission.SYSTEM_ADMIN));
        AccountingFirm first = firm("Synthetic Global Firm 1");
        AccountingFirm second = firm("Synthetic Global Firm 2");
        Company a = company(client(first, "Synthetic Global Client 1"), "Synthetic Global Company 1");
        Company b = company(client(second, "Synthetic Global Client 2"), "Synthetic Global Company 2");

        mockMvc.perform(get(PORTFOLIO).session(login(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountingFirms[*].scope", not(hasItem("GRANTED_COMPANIES"))))
                .andExpect(jsonPath("$..accountingFirmId", hasItem(first.getId().toString())))
                .andExpect(jsonPath("$..accountingFirmId", hasItem(second.getId().toString())))
                .andExpect(jsonPath("$..companyId", hasItem(a.getId().toString())))
                .andExpect(jsonPath("$..companyId", hasItem(b.getId().toString())));

        assertThat(firmGrants.findAllByUserAccountId(admin.getId())).isEmpty();
    }

    // 13 + 14
    @Test
    void navigationDoesNotGrantCompanyDataNorCreatePermissions() throws Exception {
        UserAccount supervisor = user("supervisor-no-data@example.invalid", Set.of());
        AccountingFirm firm = firm("Synthetic Firm No Data");
        Company company = company(client(firm, "Synthetic Client"), "Synthetic Company");
        firmGrant(supervisor, firm, Set.of(AccountingFirmPermission.HIERARCHY_VIEW));
        MockHttpSession session = login(supervisor);
        long companyGrantsBefore = companyGrants.count();

        mockMvc.perform(get(PORTFOLIO).session(session))
                .andExpect(jsonPath("$..companyId", hasItem(company.getId().toString())));

        mockMvc.perform(get("/api/companies/{companyId}/accounting-movements", company.getId())
                        .session(session)
                        .param("startDate", "2026-01-01")
                        .param("endDate", "2026-01-31"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/companies/{companyId}/dre", company.getId())
                        .session(session)
                        .param("startDate", "2026-01-01")
                        .param("endDate", "2026-01-31"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companies.length()").value(0));

        assertThat(companyGrants.count()).isEqualTo(companyGrantsBefore);
        assertThat(companyGrants.findAllByUserAccountId(supervisor.getId())).isEmpty();
        assertThat(firmGrants.findByUserAccountIdAndAccountingFirmId(supervisor.getId(), firm.getId())
                .orElseThrow().getPermissions())
                .containsExactly(AccountingFirmPermission.HIERARCHY_VIEW);
    }

    @Test
    void restrictedCompanyGrantKeepsItsPermissionsUnchanged() throws Exception {
        UserAccount analyst = user("analyst-permissions-kept@example.invalid", Set.of());
        Company company = company(client(firm("Synthetic Firm Kept"), "Synthetic Client"), "Synthetic Company");
        companyGrant(analyst, company, Set.of(CompanyPermission.DRE_VIEW));

        mockMvc.perform(get(PORTFOLIO).session(login(analyst))).andExpect(status().isOk());

        assertThat(companyGrants.findByUserAccountIdAndCompanyId(analyst.getId(), company.getId())
                .orElseThrow().getPermissions())
                .containsExactly(CompanyPermission.DRE_VIEW);
    }

    private UserAccount user(String email, Set<PlatformPermission> platformPermissions) {
        return users.saveAndFlush(new UserAccount("Usuário Sintético", email,
                passwordEncoder.encode("senha-segura"), platformPermissions));
    }

    private AccountingFirm firm(String name) {
        return firms.saveAndFlush(new AccountingFirm(name, true));
    }

    private Client client(AccountingFirm firm, String name) {
        return clients.saveAndFlush(new Client(firm, name, true));
    }

    private Company company(Client client, String legalName) {
        return companies.saveAndFlush(new Company(client, legalName, "%08d000199".formatted(CNPJ.incrementAndGet())));
    }

    private void firmGrant(UserAccount user, AccountingFirm firm, Set<AccountingFirmPermission> permissions) {
        firmGrants.saveAndFlush(new AccountingFirmAccessGrant(user, firm, permissions));
    }

    private CompanyAccessGrant companyGrant(UserAccount user, Company company, Set<CompanyPermission> permissions) {
        return companyGrants.saveAndFlush(new CompanyAccessGrant(user, company,
                CompanyAccessRole.CUSTOM, CostCenterScope.ALL, permissions));
    }

    private MockHttpSession login(UserAccount user) throws Exception {
        var result = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"senha-segura"}
                                """.formatted(user.getEmail())))
                .andExpect(status().isNoContent())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
