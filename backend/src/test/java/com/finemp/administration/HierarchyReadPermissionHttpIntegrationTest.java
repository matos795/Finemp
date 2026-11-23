package com.finemp.administration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.finemp.TestcontainersConfiguration;
import com.finemp.access.AccountingFirmAccessGrant;
import com.finemp.access.AccountingFirmAccessGrantRepository;
import com.finemp.access.AccountingFirmPermission;
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
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** HIERARCHY_VIEW e ACCOUNTING_SOURCE_VIEW leem; só *_MANAGE altera. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HierarchyReadPermissionHttpIntegrationTest {

    private static final String CLIENTS = "/api/accounting-firms/{firmId}/clients";
    private static final String COMPANIES = "/api/accounting-firms/{firmId}/clients/{clientId}/companies";
    private static final String SOURCES =
            "/api/accounting-firms/{firmId}/clients/{clientId}/companies/{companyId}/accounting-sources";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserAccountRepository users;
    @Autowired private AccountingFirmRepository firms;
    @Autowired private ClientRepository clients;
    @Autowired private CompanyRepository companies;
    @Autowired private AccountingSourceRepository sources;
    @Autowired private AccountingFirmAccessGrantRepository firmGrants;
    @Autowired private PasswordEncoder passwordEncoder;

    // 5 + 6
    @Test
    void hierarchyViewReadsClientsAndCompaniesButCannotChangeThem() throws Exception {
        Fixture f = fixture("hierarchy-view-reader@example.invalid", Set.of(AccountingFirmPermission.HIERARCHY_VIEW));

        mockMvc.perform(get(CLIENTS, f.firm.getId()).session(f.session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1));
        mockMvc.perform(get(COMPANIES, f.firm.getId(), f.client.getId()).session(f.session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(f.company.getId().toString()));

        mockMvc.perform(post(CLIENTS, f.firm.getId()).session(f.session).with(csrf())
                        .contentType(APPLICATION_JSON).content("{\"name\":\"Synthetic Blocked\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(COMPANIES, f.firm.getId(), f.client.getId()).session(f.session).with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"legalName\":\"Synthetic Blocked\",\"cnpj\":\"99999999000199\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(COMPANIES + "/{companyId}/assignment", f.firm.getId(), f.client.getId(), f.company.getId())
                        .session(f.session).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(CLIENTS + "/{clientId}", f.firm.getId(), f.emptyClient.getId())
                        .session(f.session).with(csrf()))
                .andExpect(status().isForbidden());

        assertThat(clients.findById(f.emptyClient.getId())).isPresent();
        assertThat(clients.findAllByAccountingFirmId(f.firm.getId())).hasSize(2);
    }

    @Test
    void hierarchyViewDoesNotReadAccountingSources() throws Exception {
        Fixture f = fixture("hierarchy-view-no-sources@example.invalid", Set.of(AccountingFirmPermission.HIERARCHY_VIEW));

        mockMvc.perform(get(SOURCES, f.firm.getId(), f.client.getId(), f.company.getId()).session(f.session))
                .andExpect(status().isForbidden());
    }

    // 7
    @Test
    void hierarchyManageKeepsReadingAndChanging() throws Exception {
        Fixture f = fixture("hierarchy-manage-kept@example.invalid", Set.of(AccountingFirmPermission.HIERARCHY_MANAGE));

        mockMvc.perform(get(CLIENTS, f.firm.getId()).session(f.session)).andExpect(status().isOk());
        mockMvc.perform(get(COMPANIES, f.firm.getId(), f.client.getId()).session(f.session)).andExpect(status().isOk());
        mockMvc.perform(post(CLIENTS, f.firm.getId()).session(f.session).with(csrf())
                        .contentType(APPLICATION_JSON).content("{\"name\":\"Synthetic Created\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(delete(CLIENTS + "/{clientId}", f.firm.getId(), f.emptyClient.getId())
                        .session(f.session).with(csrf()))
                .andExpect(status().isNoContent());
    }

    // 8 + 9
    @Test
    void accountingSourceViewReadsButCannotChangeSources() throws Exception {
        Fixture f = fixture("source-view-reader@example.invalid", Set.of(AccountingFirmPermission.ACCOUNTING_SOURCE_VIEW));

        mockMvc.perform(get(SOURCES, f.firm.getId(), f.client.getId(), f.company.getId()).session(f.session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(f.source.getId().toString()));

        mockMvc.perform(post(SOURCES, f.firm.getId(), f.client.getId(), f.company.getId()).session(f.session).with(csrf())
                        .contentType(APPLICATION_JSON).content("{\"type\":\"QUESTOR\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(SOURCES + "/{sourceId}/external-reference",
                        f.firm.getId(), f.client.getId(), f.company.getId(), f.source.getId())
                        .session(f.session).with(csrf())
                        .contentType(APPLICATION_JSON).content("{\"externalReference\":\"synthetic-ref\"}"))
                .andExpect(status().isForbidden());

        assertThat(sources.searchByCompany(f.company.getId(), "", org.springframework.data.domain.PageRequest.of(0, 10))
                .getTotalElements()).isEqualTo(1);
        assertThat(sources.findById(f.source.getId()).orElseThrow().getExternalReference()).isNull();
    }

    @Test
    void accountingSourceViewDoesNotReadTheHierarchyListing() throws Exception {
        Fixture f = fixture("source-view-no-hierarchy@example.invalid", Set.of(AccountingFirmPermission.ACCOUNTING_SOURCE_VIEW));

        mockMvc.perform(get(CLIENTS, f.firm.getId()).session(f.session)).andExpect(status().isForbidden());
    }

    @Test
    void accountingSourceManageKeepsReadingSources() throws Exception {
        Fixture f = fixture("source-manage-kept@example.invalid", Set.of(AccountingFirmPermission.ACCOUNTING_SOURCE_MANAGE));

        mockMvc.perform(get(SOURCES, f.firm.getId(), f.client.getId(), f.company.getId()).session(f.session))
                .andExpect(status().isOk());
    }

    // 11
    @Test
    void readPermissionsStayInsideTheirAccountingFirm() throws Exception {
        Fixture f = fixture("read-isolation@example.invalid",
                Set.of(AccountingFirmPermission.HIERARCHY_VIEW, AccountingFirmPermission.ACCOUNTING_SOURCE_VIEW));
        AccountingFirm other = firms.saveAndFlush(new AccountingFirm("Synthetic Other Firm", true));
        Client otherClient = clients.saveAndFlush(new Client(other, "Synthetic Other Client", true));
        Company otherCompany = companies.saveAndFlush(new Company(otherClient, "Synthetic Other Company", "88888888000188"));

        mockMvc.perform(get(CLIENTS, other.getId()).session(f.session)).andExpect(status().isForbidden());
        mockMvc.perform(get(COMPANIES, other.getId(), otherClient.getId()).session(f.session)).andExpect(status().isForbidden());
        mockMvc.perform(get(SOURCES, other.getId(), otherClient.getId(), otherCompany.getId()).session(f.session))
                .andExpect(status().isForbidden());
        // Rota cruzada: cliente de outra contabilidade sob a contabilidade autorizada.
        mockMvc.perform(get(COMPANIES, f.firm.getId(), otherClient.getId()).session(f.session))
                .andExpect(status().isNotFound());
    }

    private record Fixture(MockHttpSession session, AccountingFirm firm, Client client, Client emptyClient,
                           Company company, AccountingSource source) { }

    private Fixture fixture(String email, Set<AccountingFirmPermission> permissions) throws Exception {
        UserAccount user = users.saveAndFlush(new UserAccount("Usuário Sintético", email,
                passwordEncoder.encode("senha-segura"), Set.of()));
        AccountingFirm firm = firms.saveAndFlush(new AccountingFirm("Synthetic Read Firm", true));
        Client client = clients.saveAndFlush(new Client(firm, "Synthetic Client", true));
        Client emptyClient = clients.saveAndFlush(new Client(firm, "Synthetic Empty Client", true));
        Company company = companies.saveAndFlush(new Company(client, "Synthetic Company", "77777777000177"));
        AccountingSource source = sources.saveAndFlush(new AccountingSource(company, AccountingSourceType.QUESTOR, null, true));
        firmGrants.saveAndFlush(new AccountingFirmAccessGrant(user, firm, permissions));
        var result = mockMvc.perform(post("/api/auth/login").with(csrf()).contentType(APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"senha-segura\"}".formatted(email)))
                .andExpect(status().isNoContent()).andReturn();
        return new Fixture((MockHttpSession) result.getRequest().getSession(false), firm, client, emptyClient, company, source);
    }
}
