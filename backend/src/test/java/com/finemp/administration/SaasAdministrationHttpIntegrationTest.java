package com.finemp.administration;

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
import com.finemp.access.PlatformPermission;
import com.finemp.access.UserAccount;
import com.finemp.access.UserAccountRepository;
import com.finemp.accountingfirm.AccountingFirm;
import com.finemp.accountingfirm.AccountingFirmRepository;
import com.finemp.client.Client;
import com.finemp.client.ClientRepository;
import com.finemp.company.Company;
import com.finemp.company.CompanyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SaasAdministrationHttpIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private AccountingFirmRepository accountingFirmRepository;

    @Autowired
    private AccountingFirmAccessGrantRepository accountingFirmAccessGrantRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private CompanyClientAssignmentEventRepository assignmentEventRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void shouldRejectACompanyOutsideTheClientInTheAuthorizedRoute() throws Exception {
        UserAccount actor = createUser("source-scope@example.invalid", Set.of());
        AccountingFirm firm = accountingFirmRepository.save(new AccountingFirm("Synthetic Firm", true));
        Client first = clientRepository.save(new Client(firm, "Synthetic First", true));
        Client second = clientRepository.save(new Client(firm, "Synthetic Second", true));
        Company company = companyRepository.save(new Company(first, "Synthetic Company", "22222222000122"));
        accountingFirmAccessGrantRepository.saveAndFlush(new AccountingFirmAccessGrant(actor, firm,
                Set.of(AccountingFirmPermission.ACCOUNTING_SOURCE_MANAGE)));
        MockHttpSession session = login(actor.getEmail(), "senha-segura");

        mockMvc.perform(get("/api/accounting-firms/{firmId}/clients/{clientId}/companies/{companyId}/accounting-sources",
                        firm.getId(), second.getId(), company.getId()).session(session))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectInvalidPaginationInsteadOfReturningServerError() throws Exception {
        MockHttpSession session = createUserAndLogin("pagination@example.invalid",
                Set.of(PlatformPermission.ACCOUNTING_FIRM_VIEW));
        mockMvc.perform(get("/api/admin/accounting-firms").session(session).param("size", "101"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/admin/accounting-firms").session(session).param("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRequireAuthenticationForAdministration() throws Exception {
        mockMvc.perform(get("/api/admin/accounting-firms"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRequirePlatformPermissionToCreateAccountingFirm() throws Exception {
        MockHttpSession session = createUserAndLogin(
                "sem-permissao@example.com",
                Set.of());

        mockMvc.perform(post("/api/admin/accounting-firms")
                        .session(session)
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"Contabilidade Bloqueada"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldCreateAndListAccountingFirmsWithPlatformPermissions() throws Exception {
        MockHttpSession session = createUserAndLogin(
                "plataforma@example.com",
                Set.of(
                        PlatformPermission.ACCOUNTING_FIRM_CREATE,
                        PlatformPermission.ACCOUNTING_FIRM_VIEW));

        mockMvc.perform(post("/api/admin/accounting-firms")
                        .session(session)
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"Contabilidade Exemplo"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Contabilidade Exemplo"))
                .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(get("/api/admin/accounting-firms")
                        .session(session)
                        .param("search", "Exemplo")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void shouldKeepClientAdministrationInsideGrantedAccountingFirm() throws Exception {
        UserAccount userAccount = createUser(
                "administrador-firm@example.com",
                Set.of());
        AccountingFirm allowedFirm = accountingFirmRepository.save(
                new AccountingFirm("Contabilidade Permitida", true));
        AccountingFirm deniedFirm = accountingFirmRepository.save(
                new AccountingFirm("Contabilidade Bloqueada", true));

        accountingFirmAccessGrantRepository.saveAndFlush(
                new AccountingFirmAccessGrant(
                        userAccount,
                        allowedFirm,
                        Set.of(AccountingFirmPermission.HIERARCHY_MANAGE)));

        MockHttpSession session = login(userAccount.getEmail(), "senha-segura");

        mockMvc.perform(post("/api/accounting-firms/{accountingFirmId}/clients", allowedFirm.getId())
                        .session(session)
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"Cliente Permitido"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Cliente Permitido"));

        mockMvc.perform(post("/api/accounting-firms/{accountingFirmId}/clients", deniedFirm.getId())
                        .session(session)
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"Cliente Indevido"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldDeleteEmptyClientButRejectClientWithCompany() throws Exception {
        UserAccount actor = createUser("delete-client@example.invalid", Set.of());
        AccountingFirm firm = accountingFirmRepository.save(
                new AccountingFirm("Synthetic Firm", true));
        Client emptyClient = clientRepository.save(
                new Client(firm, "Empty Client", true));
        Client occupiedClient = clientRepository.save(
                new Client(firm, "Occupied Client", true));
        companyRepository.save(
                new Company(occupiedClient, "Synthetic Company", "33333333000133"));

        accountingFirmAccessGrantRepository.saveAndFlush(
                new AccountingFirmAccessGrant(
                        actor,
                        firm,
                        Set.of(AccountingFirmPermission.HIERARCHY_MANAGE)));

        MockHttpSession session = login(actor.getEmail(), "senha-segura");

        mockMvc.perform(delete(
                                "/api/accounting-firms/{accountingFirmId}/clients/{clientId}",
                                firm.getId(),
                                occupiedClient.getId())
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isConflict());

        org.assertj.core.api.Assertions.assertThat(
                clientRepository.findById(occupiedClient.getId()))
                .isPresent();

        mockMvc.perform(delete(
                                "/api/accounting-firms/{accountingFirmId}/clients/{clientId}",
                                firm.getId(),
                                emptyClient.getId())
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        org.assertj.core.api.Assertions.assertThat(
                clientRepository.findById(emptyClient.getId()))
                .isEmpty();
    }

    @Test
    void shouldDeleteEmptyFirmAndOwnGrantWithoutTouchingPlatformIdentity() throws Exception {
        UserAccount actor = createUser(
                "delete-firm@example.invalid",
                Set.of(
                        PlatformPermission.ACCOUNTING_FIRM_CREATE,
                        PlatformPermission.PLATFORM_USER_MANAGE));
        AccountingFirm firm = accountingFirmRepository.save(
                new AccountingFirm("Synthetic Empty Firm", true));

        accountingFirmAccessGrantRepository.saveAndFlush(
                new AccountingFirmAccessGrant(
                        actor,
                        firm,
                        Set.of(
                                AccountingFirmPermission.HIERARCHY_MANAGE,
                                AccountingFirmPermission.USER_ACCESS_MANAGE)));

        MockHttpSession session = login(actor.getEmail(), "senha-segura");

        mockMvc.perform(delete(
                                "/api/admin/accounting-firms/{accountingFirmId}",
                                firm.getId())
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        org.assertj.core.api.Assertions.assertThat(
                accountingFirmRepository.findById(firm.getId()))
                .isEmpty();
        org.assertj.core.api.Assertions.assertThat(
                accountingFirmAccessGrantRepository
                        .findByUserAccountIdAndAccountingFirmId(
                                actor.getId(),
                                firm.getId()))
                .isEmpty();

        UserAccount preserved = userAccountRepository.findById(actor.getId())
                .orElseThrow();
        org.assertj.core.api.Assertions.assertThat(preserved.isActive())
                .isTrue();
        org.assertj.core.api.Assertions.assertThat(
                preserved.getPlatformPermissions())
                .containsExactlyInAnyOrder(
                        PlatformPermission.ACCOUNTING_FIRM_CREATE,
                        PlatformPermission.PLATFORM_USER_MANAGE);
    }

    @Test
    void shouldRejectFirmDeletionWhenHierarchyOrAnotherUserGrantExists() throws Exception {
        UserAccount actor = createUser(
                "delete-firm-guard@example.invalid",
                Set.of(
                        PlatformPermission.ACCOUNTING_FIRM_CREATE,
                        PlatformPermission.PLATFORM_USER_MANAGE));
        UserAccount another = createUser("other-member@example.invalid", Set.of());
        AccountingFirm firmWithClient = accountingFirmRepository.save(
                new AccountingFirm("Firm With Client", true));
        clientRepository.save(
                new Client(firmWithClient, "Client", true));

        AccountingFirm firmWithAnotherGrant = accountingFirmRepository.save(
                new AccountingFirm("Firm With Member", true));
        accountingFirmAccessGrantRepository.saveAndFlush(
                new AccountingFirmAccessGrant(
                        another,
                        firmWithAnotherGrant,
                        Set.of(AccountingFirmPermission.HIERARCHY_MANAGE)));

        MockHttpSession session = login(actor.getEmail(), "senha-segura");

        mockMvc.perform(delete(
                                "/api/admin/accounting-firms/{accountingFirmId}",
                                firmWithClient.getId())
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isConflict());

        mockMvc.perform(delete(
                                "/api/admin/accounting-firms/{accountingFirmId}",
                                firmWithAnotherGrant.getId())
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isConflict());

        org.assertj.core.api.Assertions.assertThat(
                accountingFirmRepository.findById(firmWithClient.getId()))
                .isPresent();
        org.assertj.core.api.Assertions.assertThat(
                accountingFirmRepository.findById(firmWithAnotherGrant.getId()))
                .isPresent();
    }

    @Test
    void shouldCreateCompanyAndAccountingSourceInsideAuthorizedHierarchy() throws Exception {
        UserAccount userAccount = createUser(
                "operador@example.com",
                Set.of());
        AccountingFirm accountingFirm = accountingFirmRepository.save(
                new AccountingFirm("Contabilidade Exemplo", true));
        Client client = clientRepository.save(
                new Client(accountingFirm, "Cliente Exemplo", true));

        accountingFirmAccessGrantRepository.saveAndFlush(
                new AccountingFirmAccessGrant(
                        userAccount,
                        accountingFirm,
                        Set.of(
                                AccountingFirmPermission.HIERARCHY_MANAGE,
                                AccountingFirmPermission.ACCOUNTING_SOURCE_MANAGE)));

        MockHttpSession session = login(userAccount.getEmail(), "senha-segura");

        var companyResult = mockMvc.perform(post(
                                "/api/accounting-firms/{accountingFirmId}/clients/{clientId}/companies",
                                accountingFirm.getId(),
                                client.getId())
                        .session(session)
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "legalName":"Empresa Exemplo Ltda",
                                  "cnpj":"12345678000190"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clientId").value(client.getId().toString()))
                .andReturn();

        String companyId = com.jayway.jsonpath.JsonPath.read(
                companyResult.getResponse().getContentAsString(),
                "$.id");

        mockMvc.perform(post(
                                "/api/accounting-firms/{accountingFirmId}/clients/{clientId}/companies/{companyId}/accounting-sources",
                                accountingFirm.getId(),
                                client.getId(),
                                companyId)
                        .session(session)
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "type":"QUESTOR",
                                  "externalReference":"empresa-externa-1"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("QUESTOR"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.connectionString").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void shouldNotSilentlyReassignCompanyFromAnotherClient() throws Exception {
        UserAccount userAccount = createUser(
                "saneamento@example.com",
                Set.of(PlatformPermission.UNASSIGNED_COMPANY_ASSIGN));
        AccountingFirm accountingFirm = accountingFirmRepository.save(
                new AccountingFirm("Contabilidade Exemplo", true));
        Client currentClient = clientRepository.save(
                new Client(accountingFirm, "Cliente Atual", true));
        Client requestedClient = clientRepository.save(
                new Client(accountingFirm, "Cliente Solicitado", true));
        Company company = companyRepository.save(
                new Company(currentClient, "Empresa Exemplo", "98765432000190"));

        accountingFirmAccessGrantRepository.saveAndFlush(
                new AccountingFirmAccessGrant(
                        userAccount,
                        accountingFirm,
                        Set.of(AccountingFirmPermission.HIERARCHY_MANAGE)));

        MockHttpSession session = login(userAccount.getEmail(), "senha-segura");

        mockMvc.perform(put(
                                "/api/accounting-firms/{accountingFirmId}/clients/{clientId}/companies/{companyId}/assignment",
                                accountingFirm.getId(),
                                requestedClient.getId(),
                                company.getId())
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldAssignLegacyCompanyAndRecordAuditEvent() throws Exception {
        UserAccount userAccount = createUser(
                "saneamento-controlado@example.com",
                Set.of(PlatformPermission.UNASSIGNED_COMPANY_ASSIGN));
        AccountingFirm accountingFirm = accountingFirmRepository.save(
                new AccountingFirm("Contabilidade Exemplo", true));
        Client client = clientRepository.save(
                new Client(accountingFirm, "Cliente Correto", true));
        Company legacyCompany = companyRepository.save(
                new Company("Empresa Legada", "45678901000190"));

        accountingFirmAccessGrantRepository.saveAndFlush(
                new AccountingFirmAccessGrant(
                        userAccount,
                        accountingFirm,
                        Set.of(AccountingFirmPermission.HIERARCHY_MANAGE)));

        MockHttpSession session = login(userAccount.getEmail(), "senha-segura");

        mockMvc.perform(put(
                                "/api/accounting-firms/{accountingFirmId}/clients/{clientId}/companies/{companyId}/assignment",
                                accountingFirm.getId(),
                                client.getId(),
                                legacyCompany.getId())
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value(client.getId().toString()));

        org.assertj.core.api.Assertions.assertThat(assignmentEventRepository.count())
                .isEqualTo(1);
    }

    private MockHttpSession createUserAndLogin(
            String email,
            Set<PlatformPermission> platformPermissions) throws Exception {
        UserAccount userAccount = createUser(email, platformPermissions);
        return login(userAccount.getEmail(), "senha-segura");
    }

    private UserAccount createUser(
            String email,
            Set<PlatformPermission> platformPermissions) {
        return userAccountRepository.saveAndFlush(
                new UserAccount(
                        "Usuário Exemplo",
                        email,
                        passwordEncoder.encode("senha-segura"),
                        platformPermissions));
    }

    private MockHttpSession login(String email, String password) throws Exception {
        var result = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "email":"%s",
                                  "password":"%s"
                                }
                                """.formatted(email, password)))
                .andExpect(status().isNoContent())
                .andReturn();

        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
