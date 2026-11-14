package com.finemp.security;

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
import java.util.Set;
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
class AssetSecurityHttpIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private UserAccountRepository users;
    @Autowired private CompanyRepository companies;
    @Autowired private CompanyAccessGrantRepository grants;

    @Test
    void assetViewerCanReadButCannotCreate() throws Exception {
        var actor = users.saveAndFlush(new UserAccount(
                "Synthetic Asset Viewer",
                "asset-viewer@example.invalid",
                "hash"));
        var company = companies.saveAndFlush(new Company(
                "Synthetic Asset Company",
                "66666666000191"));

        grants.saveAndFlush(new CompanyAccessGrant(
                actor,
                company,
                CompanyAccessRole.VIEWER,
                CostCenterScope.ALL,
                Set.of(CompanyPermission.ASSET_VIEW)));

        mvc.perform(get("/api/companies/{companyId}/assets", company.getId())
                        .with(user(new UserAccountPrincipal(actor))))
                .andExpect(status().isOk());

        mvc.perform(post("/api/companies/{companyId}/assets", company.getId())
                        .with(user(new UserAccountPrincipal(actor)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validAssetJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    void assetManagerGetsMoneyAsStringsAndDuplicateTagConflicts() throws Exception {
        var actor = users.saveAndFlush(new UserAccount(
                "Synthetic Asset Money Manager",
                "asset-money-manager@example.invalid",
                "hash"));
        var company = companies.saveAndFlush(new Company(
                "Synthetic Asset Money Company",
                "66666666000167"));

        grants.saveAndFlush(new CompanyAccessGrant(
                actor,
                company,
                CompanyAccessRole.ADMIN,
                CostCenterScope.ALL,
                Set.of(CompanyPermission.ASSET_MANAGE)));

        mvc.perform(post("/api/companies/{companyId}/assets", company.getId())
                        .with(user(new UserAccountPrincipal(actor)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validAssetJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.acquisitionValue").value("1200.00"))
                .andExpect(jsonPath("$.monthlyDepreciation").value("20.00"))
                .andExpect(jsonPath("$.residualPercent").value("0.00"));

        mvc.perform(post("/api/companies/{companyId}/assets", company.getId())
                        .with(user(new UserAccountPrincipal(actor)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validAssetJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ASSET_DUPLICATE_TAG"));
    }

    @Test
    void assetManagerCanCreateAndOtherCompanyRemainsOutsideItsScope() throws Exception {
        var actor = users.saveAndFlush(new UserAccount(
                "Synthetic Asset Manager",
                "asset-manager@example.invalid",
                "hash"));
        var owned = companies.saveAndFlush(new Company(
                "Synthetic Owned Asset Company",
                "66666666000183"));
        var foreign = companies.saveAndFlush(new Company(
                "Synthetic Foreign Asset Company",
                "66666666000175"));

        grants.saveAndFlush(new CompanyAccessGrant(
                actor,
                owned,
                CompanyAccessRole.ADMIN,
                CostCenterScope.ALL,
                Set.of(CompanyPermission.ASSET_MANAGE)));

        mvc.perform(post("/api/companies/{companyId}/assets", owned.getId())
                        .with(user(new UserAccountPrincipal(actor)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validAssetJson()))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/companies/{companyId}/assets", foreign.getId())
                        .with(user(new UserAccountPrincipal(actor))))
                .andExpect(status().isForbidden());
    }

    private static String validAssetJson() {
        return """
                {
                  "assetTag":"PAT-0001",
                  "description":"Notebook sintético",
                  "category":"IT",
                  "acquisitionDate":"2026-01-10",
                  "acquisitionValue":"1200.00",
                  "quantity":1,
                  "usefulLifeMonths":12,
                  "residualPercent":"0.00",
                  "depreciable":true,
                  "status":"IN_USE",
                  "expectedVersion":null
                }
                """;
    }
}
