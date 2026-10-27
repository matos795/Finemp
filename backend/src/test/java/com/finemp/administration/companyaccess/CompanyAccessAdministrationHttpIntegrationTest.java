package com.finemp.administration.companyaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.finemp.TestcontainersConfiguration;
import com.finemp.access.*;
import com.finemp.accountingfirm.*;
import com.finemp.client.*;
import com.finemp.company.*;
import com.finemp.security.UserAccountPrincipal;
import jakarta.persistence.EntityManager;
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
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class CompanyAccessAdministrationHttpIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private UserAccountRepository users;
    @Autowired private AccountingFirmRepository firms;
    @Autowired private AccountingFirmAccessGrantRepository firmGrants;
    @Autowired private ClientRepository clients;
    @Autowired private CompanyRepository companies;
    @Autowired private CompanyAccessGrantRepository grants;
    @Autowired private EntityManager em;
    @Autowired private JdbcTemplate jdbc;
    private AccountingFirm firm;
    private Client client;
    private Company company;
    private UserAccount actor;
    private UserAccount target;

    @BeforeEach
    void prepareSyntheticScope() {
        firm = firms.saveAndFlush(new AccountingFirm("Synthetic Firm", true));
        client = clients.saveAndFlush(new Client(firm, "Synthetic Client", true));
        company = companies.saveAndFlush(new Company(client, "Synthetic Company", "15151515000115"));
        actor = account();
        target = account();
        firmGrants.saveAndFlush(new AccountingFirmAccessGrant(actor, firm, Set.of(AccountingFirmPermission.USER_ACCESS_MANAGE)));
        firmGrants.saveAndFlush(new AccountingFirmAccessGrant(target, firm, Set.of()));
    }

    @Test
    void shouldRequireAuthenticationCsrfAndMatchingParentScope() throws Exception {
        mvc.perform(get(route(company))).andExpect(status().isUnauthorized());
        mvc.perform(put(route(company) + "/" + target.getId()).with(user(new UserAccountPrincipal(actor)))
                        .contentType(APPLICATION_JSON).content(payload("[]")))
                .andExpect(status().isForbidden());
        var stranger = account();
        mvc.perform(get(route(company)).with(user(new UserAccountPrincipal(stranger))))
                .andExpect(status().isForbidden());
        var foreignClient = clients.saveAndFlush(new Client(firms.saveAndFlush(new AccountingFirm("Other", true)), "Other", true));
        var foreignCompany = companies.saveAndFlush(new Company(foreignClient, "Other", "16161616000116"));
        mvc.perform(get(route(foreignCompany)).with(user(new UserAccountPrincipal(actor))))
                .andExpect(status().isNotFound());
        mvc.perform(get(route(company).replace(client.getId().toString(), foreignClient.getId().toString()))
                        .with(user(new UserAccountPrincipal(actor))))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldGrantOnlyExplicitPermissionsAndAuditWithoutGrantingTheAdministratorFinancialAccess() throws Exception {
        mvc.perform(put(route(company) + "/" + target.getId()).with(user(new UserAccountPrincipal(actor))).with(csrf())
                        .contentType(APPLICATION_JSON).content(payload("[\"DRE_VIEW\"]")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.companyId").value(company.getId().toString()))
                .andExpect(jsonPath("$.permissions[0]").value("DRE_VIEW"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
        em.flush();
        assertThat(grants.findAllByUserAccountId(actor.getId())).isEmpty();
        assertThat(target.getPlatformPermissions()).isEmpty();
        assertThat(target.getPasswordHash()).isEqualTo("hash");
        mvc.perform(get("/api/companies/{id}/accounting-movements", company.getId())
                        .with(user(new UserAccountPrincipal(target))).param("startDate", "2026-07-01").param("endDate", "2026-07-31"))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM access_audit_events WHERE subject_user_account_id = ? AND company_id = ?",
                Long.class, target.getId(), company.getId())).isEqualTo(1L);
    }

    @Test
    void shouldRequireExplicitFirmMembershipEvenForGlobalAdministrator() throws Exception {
        var root = users.saveAndFlush(new UserAccount("Synthetic Root", UUID.randomUUID() + "@example.invalid", "hash",
                Set.of(PlatformPermission.PLATFORM_USER_MANAGE)));
        var outsider = account();
        mvc.perform(put(route(company) + "/" + outsider.getId()).with(user(new UserAccountPrincipal(root))).with(csrf())
                        .contentType(APPLICATION_JSON).content(payload("[]")))
                .andExpect(status().isConflict());
        assertThat(grants.findAllByUserAccountId(outsider.getId())).isEmpty();
    }

    @Test
    void shouldRefuseRestrictedScopeRatherThanSilentlyBroadeningIt() throws Exception {
        grants.saveAndFlush(new CompanyAccessGrant(target, company, CompanyAccessRole.VIEWER, CostCenterScope.ALL,
                Set.of(CompanyPermission.DRE_VIEW)));
        mvc.perform(put(route(company) + "/" + target.getId()).with(user(new UserAccountPrincipal(actor))).with(csrf())
                        .contentType(APPLICATION_JSON).content(payload("[]").replace("ALL", "RESTRICTED")))
                .andExpect(status().isConflict());
        assertThat(grants.findByUserAccountIdAndCompanyId(target.getId(), company.getId()).orElseThrow().getPermissions())
                .containsExactly(CompanyPermission.DRE_VIEW);
    }

    @Test
    void shouldSearchAndPageOnlyGrantedCompanyUsersWithoutCredentialOrPermissionCollectionLeak() throws Exception {
        grants.saveAndFlush(new CompanyAccessGrant(target, company));
        mvc.perform(get(route(company)).with(user(new UserAccountPrincipal(actor)))
                        .param("search", target.getEmail().toUpperCase(java.util.Locale.ROOT)).param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(target.getId().toString()))
                .andExpect(jsonPath("$.items[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.items[0].permissions").doesNotExist());
    }

    @Test
    void shouldRevokeOnlyOneCompanyAndKeepIdentityFirmMembershipAndOtherCompany() throws Exception {
        var other = companies.saveAndFlush(new Company(client, "Other Synthetic", "17171717000117"));
        grants.saveAndFlush(new CompanyAccessGrant(target, company));
        grants.saveAndFlush(new CompanyAccessGrant(target, other, CompanyAccessRole.VIEWER, CostCenterScope.ALL, Set.of(CompanyPermission.DRE_VIEW)));
        for (int i = 0; i < 2; i++) {
            mvc.perform(delete(route(company) + "/" + target.getId()).with(user(new UserAccountPrincipal(actor))).with(csrf()))
                    .andExpect(status().isNoContent());
        }
        em.flush();
        assertThat(grants.findByUserAccountIdAndCompanyId(target.getId(), company.getId())).isEmpty();
        assertThat(grants.findByUserAccountIdAndCompanyId(target.getId(), other.getId())).isPresent();
        assertThat(firmGrants.findByUserAccountIdAndAccountingFirmId(target.getId(), firm.getId())).isPresent();
        assertThat(target.isActive()).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM access_audit_events WHERE subject_user_account_id = ? AND action = 'COMPANY_ACCESS_REVOKED'",
                Long.class, target.getId())).isEqualTo(1L);
    }

    @Test
    void shouldWhitelistPayloadAndIgnoreAttemptsToAssignGlobalPermissionsOrAnotherCompany() throws Exception {
        var body = payload("[]").replace("}", ",\"companyId\":\"" + UUID.randomUUID()
                + "\",\"platformPermissions\":[\"PLATFORM_USER_MANAGE\"],\"active\":false}");
        mvc.perform(put(route(company) + "/" + target.getId()).with(user(new UserAccountPrincipal(actor))).with(csrf())
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.permissions.length()").value(0));
        assertThat(target.getPlatformPermissions()).isEmpty();
        assertThat(target.isActive()).isTrue();
        assertThat(grants.findAllByUserAccountId(target.getId())).hasSize(1);
        mvc.perform(get("/api/companies/{id}/accounting-movements", company.getId())
                        .with(user(new UserAccountPrincipal(target))).param("startDate", "2026-07-01").param("endDate", "2026-07-31"))
                .andExpect(status().isForbidden());
    }

    private UserAccount account() {
        return users.saveAndFlush(new UserAccount("Synthetic User", UUID.randomUUID() + "@example.invalid", "hash"));
    }
    private String route(Company selected) {
        return "/api/admin/accounting-firms/" + firm.getId() + "/clients/" + client.getId() + "/companies/" + selected.getId() + "/users";
    }
    private String payload(String permissions) {
        return "{\"role\":\"ADMIN\",\"costCenterScope\":\"ALL\",\"permissions\":" + permissions + "}";
    }
}
