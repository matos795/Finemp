package com.finemp.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.finemp.TestcontainersConfiguration;
import com.finemp.access.CompanyAccessGrant;
import com.finemp.access.CompanyAccessGrantRepository;
import com.finemp.access.CompanyAccessRole;
import com.finemp.access.CompanyPermission;
import com.finemp.access.CostCenterScope;
import com.finemp.access.UserAccount;
import com.finemp.access.UserAccountRepository;
import com.finemp.company.Company;
import com.finemp.company.CompanyRepository;
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
class ContractSecurityHttpIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private UserAccountRepository users;
    @Autowired private CompanyRepository companies;
    @Autowired private CompanyAccessGrantRepository grants;

    @Test
    void contractViewReachesControllerInsteadOfCompanyDenyAll() throws Exception {
        var actor = users.saveAndFlush(new UserAccount(
                "Synthetic Contract Viewer",
                "contract-viewer@example.invalid",
                "hash"));
        var company = companies.saveAndFlush(new Company(
                "Synthetic Contract Company",
                "55555555000198"));

        grants.saveAndFlush(new CompanyAccessGrant(
                actor,
                company,
                CompanyAccessRole.VIEWER,
                CostCenterScope.ALL,
                Set.of(CompanyPermission.CONTRACT_VIEW)));

        mvc.perform(get("/api/companies/{companyId}/contracts", company.getId())
                        .with(user(new UserAccountPrincipal(actor))))
                .andExpect(status().isOk());
    }

    @Test
    void contractViewerReachesMonthlyBillingPreview() throws Exception {
        var actor = users.saveAndFlush(new UserAccount(
                "Synthetic Billing Viewer",
                "billing-viewer@example.invalid",
                "hash"));
        var company = companies.saveAndFlush(new Company(
                "Synthetic Billing Company",
                "55555555000180"));

        grants.saveAndFlush(new CompanyAccessGrant(
                actor,
                company,
                CompanyAccessRole.VIEWER,
                CostCenterScope.ALL,
                Set.of(CompanyPermission.CONTRACT_VIEW)));

        mvc.perform(get(
                        "/api/companies/{companyId}/contracts/billing-preview",
                        company.getId())
                        .with(user(new UserAccountPrincipal(actor)))
                        .param("month", "2026-11"))
                .andExpect(status().isOk());
    }

    @Test
    void contractPartnerLookupReachesControllerForContractManager() throws Exception {
        var actor = users.saveAndFlush(new UserAccount(
                "Synthetic Contract Manager",
                "contract-manager@example.invalid",
                "hash"));
        var company = companies.saveAndFlush(new Company(
                "Synthetic Contract Managed Company",
                "55555555000171"));

        grants.saveAndFlush(new CompanyAccessGrant(
                actor,
                company,
                CompanyAccessRole.VIEWER,
                CostCenterScope.ALL,
                Set.of(CompanyPermission.CONTRACT_MANAGE)));

        mvc.perform(get("/api/companies/{companyId}/contracts/partners", company.getId())
                        .with(user(new UserAccountPrincipal(actor))))
                .andExpect(status().isOk());
    }
}
