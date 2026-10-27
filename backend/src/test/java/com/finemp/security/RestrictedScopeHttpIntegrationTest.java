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
class RestrictedScopeHttpIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private UserAccountRepository users;
    @Autowired private CompanyRepository companies;
    @Autowired private CompanyAccessGrantRepository grants;

    @Test
    void shouldRejectDreAndMovementsForRestrictedGrantEvenWithViewPermission() throws Exception {
        var actor = users.saveAndFlush(new UserAccount("Synthetic Restricted", "restricted@example.invalid", "hash"));
        var company = companies.saveAndFlush(new Company("Synthetic Restricted", "55555555000155"));
        grants.saveAndFlush(new CompanyAccessGrant(actor, company, CompanyAccessRole.VIEWER,
                CostCenterScope.RESTRICTED, Set.of(CompanyPermission.DRE_VIEW)));

        for (String suffix : new String[]{"dre", "accounting-movements"}) {
            mvc.perform(get("/api/companies/{companyId}/" + suffix, company.getId())
                            .with(user(new UserAccountPrincipal(actor)))
                            .param("startDate", "2026-07-01").param("endDate", "2026-07-31"))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/auth/me").with(user(new UserAccountPrincipal(actor))))
                .andExpect(status().isOk());
    }
}
