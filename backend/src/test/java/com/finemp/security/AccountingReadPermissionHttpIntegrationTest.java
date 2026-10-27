package com.finemp.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.finemp.TestcontainersConfiguration;
import com.finemp.access.*;
import com.finemp.company.Company;
import com.finemp.company.CompanyRepository;
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
class AccountingReadPermissionHttpIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private UserAccountRepository users;
    @Autowired private CompanyRepository companies;
    @Autowired private CompanyAccessGrantRepository grants;

    @Test
    void shouldDenyEmptyGrantAndRoleLabelWithoutExplicitPermission() throws Exception {
        var actor = actor();
        var company = company();
        grants.saveAndFlush(new CompanyAccessGrant(actor, company, CompanyAccessRole.ADMIN,
                CostCenterScope.ALL, Set.of()));
        expect(actor, company, "accounting-movements", 403);
        expect(actor, company, "accounting-accounts/classifications", 403);
    }

    @Test
    void shouldNotTurnDreOrPlatformAdministrationIntoFullLedgerAccess() throws Exception {
        var actor = users.saveAndFlush(new UserAccount("Synthetic Root", UUID.randomUUID() + "@example.invalid",
                "hash", Set.of(PlatformPermission.PLATFORM_USER_MANAGE)));
        var company = company();
        grants.saveAndFlush(new CompanyAccessGrant(actor, company, CompanyAccessRole.VIEWER,
                CostCenterScope.ALL, Set.of(CompanyPermission.DRE_VIEW)));
        expect(actor, company, "accounting-movements", 403);
        expect(actor, company, "accounting-accounts/classifications", 403);
        expect(actor, company, "dre", 200);
    }

    @Test
    void shouldKeepEachReadPermissionInsideItsCompanyAndOperation() throws Exception {
        var actor = actor();
        var company = company();
        var other = company();
        grants.saveAndFlush(new CompanyAccessGrant(actor, company, CompanyAccessRole.CUSTOM,
                CostCenterScope.ALL, Set.of(CompanyPermission.ACCOUNTING_MOVEMENT_VIEW)));
        expect(actor, company, "accounting-movements", 200);
        expect(actor, company, "accounting-accounts/classifications", 403);
        expect(actor, other, "accounting-movements", 403);
        expect(actor, company, "not-an-allowed-company-operation", 403);
    }

    @Test
    void shouldAllowClassificationViewOrManageButNeitherGrantsLedgerRead() throws Exception {
        for (var permission : Set.of(CompanyPermission.ACCOUNT_CLASSIFICATION_VIEW,
                CompanyPermission.ACCOUNT_CLASSIFICATION_MANAGE)) {
            var actor = actor();
            var company = company();
            grants.saveAndFlush(new CompanyAccessGrant(actor, company, CompanyAccessRole.CUSTOM,
                    CostCenterScope.ALL, Set.of(permission)));
            expect(actor, company, "accounting-accounts/classifications", 200);
            expect(actor, company, "accounting-movements", 403);
        }
    }

    @Test
    void shouldKeepRestrictedScopeClosedEvenWithBothReadPermissions() throws Exception {
        var actor = actor();
        var company = company();
        grants.saveAndFlush(new CompanyAccessGrant(actor, company, CompanyAccessRole.ADMIN,
                CostCenterScope.RESTRICTED, Set.of(CompanyPermission.ACCOUNTING_MOVEMENT_VIEW,
                        CompanyPermission.ACCOUNT_CLASSIFICATION_VIEW)));
        expect(actor, company, "accounting-movements", 403);
        expect(actor, company, "accounting-accounts/classifications", 403);
    }

    private void expect(UserAccount actor, Company company, String suffix, int expected) throws Exception {
        mvc.perform(get("/api/companies/{companyId}/" + suffix, company.getId())
                        .with(user(new UserAccountPrincipal(actor)))
                        .param("startDate", "2026-07-01").param("endDate", "2026-07-31"))
                .andExpect(status().is(expected));
    }
    private UserAccount actor() {
        return users.saveAndFlush(new UserAccount("Synthetic Reader", UUID.randomUUID() + "@example.invalid", "hash"));
    }
    private Company company() {
        return companies.saveAndFlush(new Company("Synthetic Read Boundary", "14141414000114"));
    }
}
