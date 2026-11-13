package com.finemp.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.finemp.TestcontainersConfiguration;
import com.finemp.access.*;
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
class WorkingCapitalCycleSecurityHttpIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private UserAccountRepository users;
    @Autowired private CompanyRepository companies;
    @Autowired private CompanyAccessGrantRepository grants;

    @Test
    void authenticatedUserWithoutCompanyPermissionGetsForbidden() throws Exception {
        var actor = users.saveAndFlush(new UserAccount(
                "Synthetic Cycle Unauthorized",
                "cycle-unauthorized@example.invalid",
                "hash"));
        var company = companies.saveAndFlush(new Company(
                "Synthetic Cycle Unauthorized Company",
                "55555555000170"));

        mvc.perform(get(
                        "/api/companies/{companyId}/financial-reports/working-capital-cycle",
                        company.getId())
                        .with(user(new UserAccountPrincipal(actor)))
                        .param("from", "2026-11-01")
                        .param("to", "2026-11-30"))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidCycleParametersGetBadRequest() throws Exception {
        var actor = users.saveAndFlush(new UserAccount(
                "Synthetic Cycle Invalid",
                "cycle-invalid@example.invalid",
                "hash"));
        var company = companies.saveAndFlush(new Company(
                "Synthetic Cycle Invalid Company",
                "55555555000169"));

        grants.saveAndFlush(new CompanyAccessGrant(
                actor,
                company,
                CompanyAccessRole.VIEWER,
                CostCenterScope.ALL,
                Set.of(CompanyPermission.FINANCIAL_ENTRY_VIEW)));

        mvc.perform(get(
                        "/api/companies/{companyId}/financial-reports/working-capital-cycle",
                        company.getId())
                        .with(user(new UserAccountPrincipal(actor)))
                        .param("from", "2026-11-30")
                        .param("to", "2026-11-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void financialViewerReachesWorkingCapitalCycleInsteadOfCompanyDenyAll() throws Exception {
        var actor = users.saveAndFlush(new UserAccount(
                "Synthetic Cycle Viewer",
                "cycle-viewer@example.invalid",
                "hash"));
        var company = companies.saveAndFlush(new Company(
                "Synthetic Cycle Company",
                "55555555000171"));

        grants.saveAndFlush(new CompanyAccessGrant(
                actor,
                company,
                CompanyAccessRole.VIEWER,
                CostCenterScope.ALL,
                Set.of(CompanyPermission.FINANCIAL_ENTRY_VIEW)));

        mvc.perform(get(
                        "/api/companies/{companyId}/financial-reports/working-capital-cycle",
                        company.getId())
                        .with(user(new UserAccountPrincipal(actor)))
                        .param("from", "2026-11-01")
                        .param("to", "2026-11-30"))
                .andExpect(status().isOk());
    }
}
