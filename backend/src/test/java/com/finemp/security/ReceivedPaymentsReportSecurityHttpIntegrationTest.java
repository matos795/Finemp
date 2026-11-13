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
class ReceivedPaymentsReportSecurityHttpIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private UserAccountRepository users;
    @Autowired private CompanyRepository companies;
    @Autowired private CompanyAccessGrantRepository grants;

    @Test
    void financialViewerReachesReceivedPaymentsReportInsteadOfCompanyDenyAll() throws Exception {
        var actor = users.saveAndFlush(new UserAccount(
                "Synthetic Received Payments Viewer",
                "received-payments-viewer@example.invalid",
                "hash"));
        var company = companies.saveAndFlush(new Company(
                "Synthetic Received Payments Company",
                "55555555000163"));

        grants.saveAndFlush(new CompanyAccessGrant(
                actor,
                company,
                CompanyAccessRole.VIEWER,
                CostCenterScope.ALL,
                Set.of(CompanyPermission.FINANCIAL_ENTRY_VIEW)));

        mvc.perform(get(
                        "/api/companies/{companyId}/financial-reports/received-by-method",
                        company.getId())
                        .with(user(new UserAccountPrincipal(actor)))
                        .param("from", "2026-11-01")
                        .param("to", "2026-11-30"))
                .andExpect(status().isOk());
    }
}
