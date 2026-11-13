package com.finemp.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.finemp.TestcontainersConfiguration;
import com.finemp.access.*;
import com.finemp.company.*;
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
    void financialViewerReachesCycleReportInsteadOfCompanyDenyAll() throws Exception {
        var actor = users.saveAndFlush(new UserAccount(
                "Synthetic Cycle Viewer",
                "cycle-viewer@example.invalid",
                "hash"));
        var company = companies.saveAndFlush(new Company(
                "Synthetic Cycle Company",
                "56565656000156"));

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
                        .param("months", "3")
                        .param("pmeDays", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.daysInPeriod").isNumber())
                .andExpect(jsonPath("$.receivables.volume").value("0.00"))
                .andExpect(jsonPath("$.payables.volume").value("0.00"));
    }

    @Test
    void authenticatedUserWithoutFinancialPermissionIsForbidden() throws Exception {
        var actor = users.saveAndFlush(new UserAccount(
                "Synthetic Cycle Denied",
                "cycle-denied@example.invalid",
                "hash"));
        var company = companies.saveAndFlush(new Company(
                "Synthetic Cycle Denied Company",
                "57575757000157"));

        grants.saveAndFlush(new CompanyAccessGrant(
                actor,
                company,
                CompanyAccessRole.CUSTOM,
                CostCenterScope.ALL,
                Set.of(CompanyPermission.BUSINESS_PARTNER_VIEW)));

        mvc.perform(get(
                        "/api/companies/{companyId}/financial-reports/working-capital-cycle",
                        company.getId())
                        .with(user(new UserAccountPrincipal(actor)))
                        .param("months", "3")
                        .param("pmeDays", "0"))
                .andExpect(status().isForbidden());
    }
}
