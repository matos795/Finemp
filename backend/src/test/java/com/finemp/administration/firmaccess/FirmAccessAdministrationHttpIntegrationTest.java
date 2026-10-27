package com.finemp.administration.firmaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.finemp.TestcontainersConfiguration;
import com.finemp.access.AccountingFirmAccessGrant;
import com.finemp.access.AccountingFirmAccessGrantRepository;
import com.finemp.access.AccountingFirmPermission;
import com.finemp.access.CompanyAccessGrant;
import com.finemp.access.CompanyAccessGrantRepository;
import com.finemp.access.PlatformPermission;
import com.finemp.access.UserAccount;
import com.finemp.access.UserAccountRepository;
import com.finemp.accountingfirm.AccountingFirm;
import com.finemp.accountingfirm.AccountingFirmRepository;
import com.finemp.client.Client;
import com.finemp.client.ClientRepository;
import com.finemp.company.Company;
import com.finemp.company.CompanyRepository;
import com.finemp.security.UserAccountPrincipal;
import jakarta.persistence.EntityManager;
import java.util.Set;
import java.util.UUID;
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
class FirmAccessAdministrationHttpIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private UserAccountRepository users;
    @Autowired private AccountingFirmRepository firms;
    @Autowired private AccountingFirmAccessGrantRepository grants;
    @Autowired private CompanyAccessGrantRepository companyGrants;
    @Autowired private ClientRepository clients;
    @Autowired private CompanyRepository companies;
    @Autowired private EntityManager em;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void shouldRequireAuthenticationAndKeepAdministrationInsideTheGrantedFirm() throws Exception {
        var firm = firm();
        var other = firm();
        var actor = account(Set.of());
        grants.saveAndFlush(new AccountingFirmAccessGrant(actor, firm, Set.of(AccountingFirmPermission.USER_ACCESS_MANAGE)));
        mvc.perform(get(route(firm))).andExpect(status().isUnauthorized());
        mvc.perform(get(route(other)).with(user(new UserAccountPrincipal(actor)))).andExpect(status().isForbidden());
        var foreignUser = account(Set.of());
        grants.saveAndFlush(new AccountingFirmAccessGrant(foreignUser, other, Set.of()));
        mvc.perform(put(route(firm) + "/" + foreignUser.getId()).with(user(new UserAccountPrincipal(actor))).with(csrf())
                        .contentType(APPLICATION_JSON).content("{\"permissions\":[]}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldSearchAndPageOnlyMembersWithoutPermissionCollectionFetchInTheList() throws Exception {
        var firm = firm();
        var actor = account(Set.of());
        var target = account(Set.of());
        grants.saveAndFlush(new AccountingFirmAccessGrant(actor, firm, Set.of(AccountingFirmPermission.USER_ACCESS_MANAGE)));
        grants.saveAndFlush(new AccountingFirmAccessGrant(target, firm, Set.of(AccountingFirmPermission.CONNECTOR_MANAGE)));
        mvc.perform(get(route(firm)).with(user(new UserAccountPrincipal(actor)))
                        .param("search", target.getEmail().toUpperCase(java.util.Locale.ROOT)).param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(target.getId().toString()))
                .andExpect(jsonPath("$.items[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.items[0].permissions").doesNotExist());
    }

    @Test
    void shouldReplaceOnlyThisFirmsPermissionsAndAuditTheSnapshot() throws Exception {
        var firm = firm();
        var other = firm();
        var actor = account(Set.of());
        var target = account(Set.of(PlatformPermission.ACCOUNTING_FIRM_VIEW));
        grants.saveAndFlush(new AccountingFirmAccessGrant(actor, firm, Set.of(AccountingFirmPermission.USER_ACCESS_MANAGE)));
        grants.saveAndFlush(new AccountingFirmAccessGrant(target, firm, Set.of(AccountingFirmPermission.CONNECTOR_MANAGE)));
        var otherGrant = grants.saveAndFlush(new AccountingFirmAccessGrant(target, other, Set.of(AccountingFirmPermission.CONNECTOR_MANAGE)));

        mvc.perform(put(route(firm) + "/" + target.getId()).with(user(new UserAccountPrincipal(actor))).with(csrf())
                        .contentType(APPLICATION_JSON).content("{\"permissions\":[\"HIERARCHY_MANAGE\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.permissions[0]").value("HIERARCHY_MANAGE"));
        em.flush();
        assertThat(otherGrant.getPermissions()).containsExactly(AccountingFirmPermission.CONNECTOR_MANAGE);
        assertThat(target.getPlatformPermissions()).containsExactly(PlatformPermission.ACCOUNTING_FIRM_VIEW);
        assertThat(target.getPasswordHash()).isEqualTo("synthetic-hash");
        assertThat(companyGrants.findAllByUserAccountId(target.getId())).isEmpty();
        var event = jdbc.queryForMap("SELECT * FROM access_audit_events WHERE subject_user_account_id = ?", target.getId());
        assertThat(event).containsEntry("action", "FIRM_ACCESS_REPLACED").containsEntry("accounting_firm_id", firm.getId());
        assertThat(jdbc.queryForList("SELECT permission FROM access_audit_event_permissions WHERE event_id = ?",
                String.class, event.get("id"))).containsExactly("HIERARCHY_MANAGE");
    }

    @Test
    void shouldAllowGlobalAdministratorToCreateAnExplicitBindingWithoutFinancialAccess() throws Exception {
        var firm = firm();
        var actor = account(Set.of(PlatformPermission.PLATFORM_USER_MANAGE));
        var target = account(Set.of());
        mvc.perform(put(route(firm) + "/" + target.getId()).with(user(new UserAccountPrincipal(actor))).with(csrf())
                        .contentType(APPLICATION_JSON).content("{\"permissions\":[]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.permissions.length()").value(0));
        assertThat(grants.findByUserAccountIdAndAccountingFirmId(target.getId(), firm.getId())).isPresent();
        assertThat(companyGrants.findAllByUserAccountId(target.getId())).isEmpty();
    }

    @Test
    void shouldRefuseImplicitFinancialCascadeAndRevokeOnlyAfterExplicitCompanyGrantRemoval() throws Exception {
        var firm = firm();
        var actor = account(Set.of(PlatformPermission.PLATFORM_USER_MANAGE));
        var target = account(Set.of());
        grants.saveAndFlush(new AccountingFirmAccessGrant(target, firm, Set.of(AccountingFirmPermission.CONNECTOR_MANAGE)));
        var client = clients.saveAndFlush(new Client(firm, "Synthetic Client", true));
        var company = companies.saveAndFlush(new Company(client, "Synthetic Company", "12121212000112"));
        var financial = companyGrants.saveAndFlush(new CompanyAccessGrant(target, company));

        mvc.perform(delete(route(firm) + "/" + target.getId()).with(user(new UserAccountPrincipal(actor))).with(csrf()))
                .andExpect(status().isConflict());
        assertThat(companyGrants.findById(financial.getId())).isPresent();
        companyGrants.delete(financial);
        companyGrants.flush();
        mvc.perform(delete(route(firm) + "/" + target.getId()).with(user(new UserAccountPrincipal(actor))).with(csrf()))
                .andExpect(status().isNoContent());
        em.flush();
        assertThat(grants.findByUserAccountIdAndAccountingFirmId(target.getId(), firm.getId())).isEmpty();
        assertThat(target.isActive()).isTrue();
        assertThat(jdbc.queryForObject("SELECT action FROM access_audit_events WHERE subject_user_account_id = ?",
                String.class, target.getId())).isEqualTo("FIRM_ACCESS_REVOKED");
    }

    @Test
    void shouldRequireCsrfAndKeepAbsentRevocationIdempotentWithoutLeakingGlobalIdentityExistence() throws Exception {
        var firm = firm();
        var actor = account(Set.of(PlatformPermission.PLATFORM_USER_MANAGE));
        UUID missing = UUID.randomUUID();
        mvc.perform(delete(route(firm) + "/" + missing).with(user(new UserAccountPrincipal(actor))))
                .andExpect(status().isForbidden());
        mvc.perform(delete(route(firm) + "/" + missing).with(user(new UserAccountPrincipal(actor))).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM access_audit_events WHERE subject_user_account_id = ?",
                Long.class, missing)).isZero();
    }

    private UserAccount account(Set<PlatformPermission> permissions) {
        return users.saveAndFlush(new UserAccount("Synthetic Member", "firm-member-" + UUID.randomUUID()
                + "@example.invalid", "synthetic-hash", permissions));
    }
    private AccountingFirm firm() { return firms.saveAndFlush(new AccountingFirm("Synthetic Access Firm", true)); }
    private String route(AccountingFirm firm) { return "/api/admin/accounting-firms/" + firm.getId() + "/users"; }
}
