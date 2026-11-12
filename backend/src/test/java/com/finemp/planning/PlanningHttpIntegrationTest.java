package com.finemp.planning;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import com.finemp.security.UserAccountPrincipal;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class PlanningHttpIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private UserAccountRepository users;
    @Autowired private CompanyRepository companies;
    @Autowired private CompanyAccessGrantRepository grants;

    @Test
    void viewReadsAllPlanningSurfacesButCannotWriteAndCannotCrossCompany() throws Exception {
        var reader = account("reader");
        var companyA = companies.saveAndFlush(
                new Company("Synthetic Planning A", "00000000000009"));
        var companyB = companies.saveAndFlush(
                new Company("Synthetic Planning B", "00000000000010"));

        grant(reader, companyA, Set.of(CompanyPermission.PLANNING_VIEW));

        var principal = new UserAccountPrincipal(reader);

        mvc.perform(get(
                        "/api/companies/{companyId}/planning/meetings",
                        companyA.getId())
                        .with(user(principal)))
                .andExpect(status().isOk());

        mvc.perform(get(
                        "/api/companies/{companyId}/goals",
                        companyA.getId())
                        .with(user(principal)))
                .andExpect(status().isOk());

        mvc.perform(get(
                        "/api/companies/{companyId}/action-plans",
                        companyA.getId())
                        .with(user(principal)))
                .andExpect(status().isOk());

        mvc.perform(post(
                        "/api/companies/{companyId}/planning/meetings",
                        companyA.getId())
                        .with(user(principal))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Encontro sintético",
                                  "stage": "Diagnóstico",
                                  "scheduledOn": "2026-12-01"
                                }
                                """))
                .andExpect(status().isForbidden());

        mvc.perform(get(
                        "/api/companies/{companyId}/goals",
                        companyB.getId())
                        .with(user(principal)))
                .andExpect(status().isForbidden());
    }

    @Test
    void manageCreatesPlanningMeetingThroughTheRealHttpChain() throws Exception {
        var manager = account("manager");
        var company = companies.saveAndFlush(
                new Company("Synthetic Planning Manage", "00000000000011"));

        grant(manager, company, Set.of(CompanyPermission.PLANNING_MANAGE));

        mvc.perform(post(
                        "/api/companies/{companyId}/planning/meetings",
                        company.getId())
                        .with(user(new UserAccountPrincipal(manager)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Kickoff sintético",
                                  "stage": "Kickoff",
                                  "scheduledOn": "2026-12-05",
                                  "notes": "Sem dados reais"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Kickoff sintético"))
                .andExpect(jsonPath("$.stage").value("Kickoff"))
                .andExpect(jsonPath("$.version").value(0));
    }

    private UserAccount account(String label) {
        return users.saveAndFlush(new UserAccount(
                "Synthetic Planning " + label,
                "planning-" + label + "-" + UUID.randomUUID() + "@example.invalid",
                "synthetic-existing-hash",
                Set.of()));
    }

    private void grant(
            UserAccount user,
            Company company,
            Set<CompanyPermission> permissions) {

        grants.saveAndFlush(new CompanyAccessGrant(
                user,
                company,
                CompanyAccessRole.CUSTOM,
                CostCenterScope.ALL,
                permissions));
    }
}
