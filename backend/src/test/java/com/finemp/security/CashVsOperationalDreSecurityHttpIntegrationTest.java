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

@SpringBootTest(properties = {
        "finemp.finance.monthly-cash.enabled=true",
        "finemp.finance.operational-dre.enabled=true"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class CashVsOperationalDreSecurityHttpIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private UserAccountRepository users;
    @Autowired private CompanyRepository companies;
    @Autowired private CompanyAccessGrantRepository grants;

    @Test
    void financialViewerReachesComparisonInsteadOfCompanyDenyAll() throws Exception {
        var actor = users.saveAndFlush(new UserAccount(
                "Synthetic Cash DRE Viewer",
                "cash-dre-viewer@example.invalid",
                "hash"));
        var company = companies.saveAndFlush(new Company(
                "Synthetic Cash DRE Company",
                "55555555000155"));

        grants.saveAndFlush(new CompanyAccessGrant(
                actor,
                company,
                CompanyAccessRole.VIEWER,
                CostCenterScope.ALL,
                Set.of(CompanyPermission.FINANCIAL_ENTRY_VIEW)));

        mvc.perform(get(
                        "/api/companies/{companyId}/financial-reports/cash-vs-dre",
                        company.getId())
                        .with(user(new UserAccountPrincipal(actor)))
                        .param("month", "2026-11"))
                .andExpect(status().isOk());
    }
}
