package com.finemp.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class OrganizationAccessHttpIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private UserAccountRepository users;
    @Autowired private AccountingFirmRepository firms;
    @Autowired private AccountingFirmAccessGrantRepository grants;

    @Test
    void shouldRequireASession() throws Exception {
        mvc.perform(get("/api/auth/organization-access")).andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnOnlyThePrincipalsAdministrativeGrantsWithoutFinancialAccessOrSecrets() throws Exception {
        var actor = users.saveAndFlush(new UserAccount("Synthetic Actor", "context@example.invalid", "synthetic-hash",
                Set.of(PlatformPermission.ACCOUNTING_FIRM_CREATE)));
        var other = users.saveAndFlush(new UserAccount("Synthetic Other", "other-context@example.invalid", "other-hash"));
        var allowed = firms.saveAndFlush(new AccountingFirm("Synthetic Allowed", true));
        var denied = firms.saveAndFlush(new AccountingFirm("Synthetic Denied", true));
        grants.saveAndFlush(new AccountingFirmAccessGrant(actor, allowed, Set.of(AccountingFirmPermission.HIERARCHY_MANAGE)));
        grants.saveAndFlush(new AccountingFirmAccessGrant(other, denied, Set.of(AccountingFirmPermission.USER_ACCESS_MANAGE)));

        var response = mvc.perform(get("/api/auth/organization-access").with(user(new UserAccountPrincipal(actor)))
                        .param("userId", other.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.platformPermissions[0]").value("ACCOUNTING_FIRM_CREATE"))
                .andExpect(jsonPath("$.accountingFirms.length()").value(1))
                .andExpect(jsonPath("$.accountingFirms[0].accountingFirmId").value(allowed.getId().toString()))
                .andExpect(jsonPath("$.accountingFirms[0].permissions[0]").value("HIERARCHY_MANAGE"))
                .andReturn().getResponse();
        assertThat(response.getContentAsString()).doesNotContain("synthetic-hash", "other-hash", denied.getId().toString());
        mvc.perform(get("/api/auth/me").with(user(new UserAccountPrincipal(actor))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.companies.length()").value(0));
    }
}
