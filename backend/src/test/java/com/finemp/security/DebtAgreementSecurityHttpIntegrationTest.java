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
class DebtAgreementSecurityHttpIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private UserAccountRepository users;
    @Autowired private CompanyRepository companies;
    @Autowired private CompanyAccessGrantRepository grants;

    @Test
    void debtViewerReachesListInsteadOfCompanyDenyAll() throws Exception {
        var actor = users.saveAndFlush(new UserAccount(
                "Synthetic Debt Viewer",
                "debt-viewer@example.invalid",
                "hash"));
        var company = companies.saveAndFlush(new Company(
                "Synthetic Debt Company",
                "55555555000185"));

        grants.saveAndFlush(new CompanyAccessGrant(
                actor,
                company,
                CompanyAccessRole.VIEWER,
                CostCenterScope.ALL,
                Set.of(CompanyPermission.DEBT_VIEW)));

        mvc.perform(get("/api/companies/{companyId}/debts", company.getId())
                        .with(user(new UserAccountPrincipal(actor)))
                        .param("origin", "BANK"))
                .andExpect(status().isOk());
    }

    @Test
    void invalidDebtOriginReturnsBadRequestForAuthorizedViewer() throws Exception {
        var actor = users.saveAndFlush(new UserAccount(
                "Synthetic Debt Invalid",
                "debt-invalid@example.invalid",
                "hash"));
        var company = companies.saveAndFlush(new Company(
                "Synthetic Debt Invalid Company",
                "55555555000186"));

        grants.saveAndFlush(new CompanyAccessGrant(
                actor,
                company,
                CompanyAccessRole.VIEWER,
                CostCenterScope.ALL,
                Set.of(CompanyPermission.DEBT_VIEW)));

        mvc.perform(get("/api/companies/{companyId}/debts", company.getId())
                        .with(user(new UserAccountPrincipal(actor)))
                        .param("origin", "UNKNOWN"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void debtManagerReachesNarrowPartnerLookup() throws Exception {
        var actor = users.saveAndFlush(new UserAccount(
                "Synthetic Debt Manager",
                "debt-manager@example.invalid",
                "hash"));
        var company = companies.saveAndFlush(new Company(
                "Synthetic Debt Managed Company",
                "55555555000187"));

        grants.saveAndFlush(new CompanyAccessGrant(
                actor,
                company,
                CompanyAccessRole.VIEWER,
                CostCenterScope.ALL,
                Set.of(CompanyPermission.DEBT_MANAGE)));

        mvc.perform(get("/api/companies/{companyId}/debts/partners", company.getId())
                        .with(user(new UserAccountPrincipal(actor))))
                .andExpect(status().isOk());
    }
}
