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
class ServiceInvoiceSecurityHttpIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private UserAccountRepository users;
    @Autowired private CompanyRepository companies;
    @Autowired private CompanyAccessGrantRepository grants;

    @Test
    void billingViewerReachesInvoiceList() throws Exception {
        var actor = users.saveAndFlush(new UserAccount(
                "Synthetic Billing Viewer",
                "service-invoice-viewer@example.invalid",
                "hash"));
        var company = companies.saveAndFlush(new Company(
                "Synthetic Service Invoice Company",
                "55555555000182"));

        grants.saveAndFlush(new CompanyAccessGrant(
                actor,
                company,
                CompanyAccessRole.VIEWER,
                CostCenterScope.ALL,
                Set.of(CompanyPermission.BILLING_VIEW)));

        mvc.perform(get(
                        "/api/companies/{companyId}/service-invoices",
                        company.getId())
                        .with(user(new UserAccountPrincipal(actor)))
                        .param("month", "2026-11"))
                .andExpect(status().isOk());
    }

    @Test
    void userWithoutBillingPermissionGetsForbidden() throws Exception {
        var actor = users.saveAndFlush(new UserAccount(
                "Synthetic Billing Unauthorized",
                "service-invoice-noaccess@example.invalid",
                "hash"));
        var company = companies.saveAndFlush(new Company(
                "Synthetic Service Invoice No Access",
                "55555555000183"));

        mvc.perform(get(
                        "/api/companies/{companyId}/service-invoices",
                        company.getId())
                        .with(user(new UserAccountPrincipal(actor)))
                        .param("month", "2026-11"))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidMonthGetsBadRequestForAuthorizedViewer() throws Exception {
        var actor = users.saveAndFlush(new UserAccount(
                "Synthetic Billing Invalid",
                "service-invoice-invalid@example.invalid",
                "hash"));
        var company = companies.saveAndFlush(new Company(
                "Synthetic Service Invoice Invalid Company",
                "55555555000184"));

        grants.saveAndFlush(new CompanyAccessGrant(
                actor,
                company,
                CompanyAccessRole.VIEWER,
                CostCenterScope.ALL,
                Set.of(CompanyPermission.BILLING_VIEW)));

        mvc.perform(get(
                        "/api/companies/{companyId}/service-invoices",
                        company.getId())
                        .with(user(new UserAccountPrincipal(actor)))
                        .param("month", "2026-13"))
                .andExpect(status().isBadRequest());
    }
}
