package com.finemp.company;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.finemp.TestcontainersConfiguration;
import com.finemp.access.CompanyAccessGrant;
import com.finemp.access.CompanyAccessGrantRepository;
import com.finemp.access.CompanyAccessRole;
import com.finemp.access.CostCenterScope;
import com.finemp.access.UserAccount;
import com.finemp.access.UserAccountRepository;
import com.finemp.security.UserAccountPrincipal;
import java.util.Set;
import java.util.UUID;
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
class CompanyProfileHttpIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private UserAccountRepository users;
    @Autowired private CompanyRepository companies;
    @Autowired private CompanyAccessGrantRepository grants;

    @Test
    void authenticatedUserWithCompanyAccessCanReadProfileThroughSecurityChain() throws Exception {
        var user = account();
        var company = companies.saveAndFlush(
                new Company("Synthetic Profile", "00000000000004"));

        grants.saveAndFlush(new CompanyAccessGrant(
                user,
                company,
                CompanyAccessRole.CLIENT,
                CostCenterScope.ALL,
                Set.of()));

        mvc.perform(get(
                        "/api/companies/{companyId}/profile",
                        company.getId())
                        .with(user(new UserAccountPrincipal(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(company.getId().toString()))
                .andExpect(jsonPath("$.legalName").value("Synthetic Profile"))
                .andExpect(jsonPath("$.cnpj").value("00000000000004"));
    }

    @Test
    void authenticatedUserWithoutCompanyAccessCannotReadProfile() throws Exception {
        var user = account();
        var company = companies.saveAndFlush(
                new Company("Synthetic Forbidden", "00000000000005"));

        mvc.perform(get(
                        "/api/companies/{companyId}/profile",
                        company.getId())
                        .with(user(new UserAccountPrincipal(user))))
                .andExpect(status().isForbidden());
    }

    private UserAccount account() {
        return users.saveAndFlush(new UserAccount(
                "Synthetic Profile User",
                "profile-" + UUID.randomUUID() + "@example.invalid",
                "synthetic-existing-hash",
                Set.of()));
    }
}
