package com.finemp.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class SalesCrmSecurityHttpIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private UserAccountRepository users;
    @Autowired private CompanyRepository companies;
    @Autowired private CompanyAccessGrantRepository grants;

    @Test
    void crmViewerReachesPipelineAndIndicatorsInsteadOfCompanyDenyAll() throws Exception {
        var actor = users.saveAndFlush(new UserAccount(
                "Synthetic CRM Viewer",
                "crm-viewer@example.invalid",
                "hash"));
        var company = companies.saveAndFlush(new Company(
                "Synthetic CRM Company",
                "55555555000147"));

        grants.saveAndFlush(new CompanyAccessGrant(
                actor,
                company,
                CompanyAccessRole.VIEWER,
                CostCenterScope.ALL,
                Set.of(CompanyPermission.CRM_VIEW)));

        mvc.perform(get("/api/companies/{companyId}/opportunities", company.getId())
                        .with(user(new UserAccountPrincipal(actor))))
                .andExpect(status().isOk());

        mvc.perform(get("/api/companies/{companyId}/sales/indicators", company.getId())
                        .with(user(new UserAccountPrincipal(actor))))
                .andExpect(status().isOk());
    }
}
