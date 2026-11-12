package com.finemp.audit;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.finemp.TestcontainersConfiguration;
import com.finemp.access.PlatformPermission;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AuditEventQueryHttpIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private UserAccountRepository users;
    @Autowired private CompanyRepository companies;
    @Autowired private AccessAuditRecorder recorder;

    @Test
    void companyAuditRouteReachesServiceAndNeverCrossesCompanyBoundary() throws Exception {
        var admin = account(Set.of(PlatformPermission.SYSTEM_ADMIN));
        var companyA = companies.saveAndFlush(new Company("Synthetic A", "00000000000001"));
        var companyB = companies.saveAndFlush(new Company("Synthetic B", "00000000000002"));

        recorder.record(
                AccessAuditAction.PLATFORM_PERMISSIONS_REPLACED,
                admin.getId(),
                admin.getId(),
                null,
                companyA.getId(),
                Set.of(PlatformPermission.SYSTEM_ADMIN));

        recorder.record(
                AccessAuditAction.USER_REACTIVATED,
                admin.getId(),
                admin.getId(),
                null,
                companyB.getId(),
                Set.of(PlatformPermission.SYSTEM_ADMIN));

        mvc.perform(get(
                        "/api/companies/{companyId}/audit-events",
                        companyA.getId())
                        .with(user(new UserAccountPrincipal(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].companyId")
                        .value(companyA.getId().toString()))
                .andExpect(jsonPath("$.items[0].action")
                        .value("PLATFORM_PERMISSIONS_REPLACED"));
    }

    @Test
    void normalAuthenticatedUserCannotReadCompanyAudit() throws Exception {
        var user = account(Set.of());
        var company = companies.saveAndFlush(
                new Company("Synthetic C", "00000000000003"));

        mvc.perform(get(
                        "/api/companies/{companyId}/audit-events",
                        company.getId())
                        .with(user(new UserAccountPrincipal(user))))
                .andExpect(status().isForbidden());
    }

    private UserAccount account(Set<PlatformPermission> permissions) {
        return users.saveAndFlush(new UserAccount(
                "Synthetic Audit",
                "audit-" + UUID.randomUUID() + "@example.invalid",
                "synthetic-existing-hash",
                permissions));
    }
}
