package com.finemp.security;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.finemp.TestcontainersConfiguration;
import com.finemp.access.CompanyAccessGrant;
import com.finemp.access.CompanyAccessGrantRepository;
import com.finemp.access.CompanyAccessRole;
import com.finemp.access.CompanyPermission;
import com.finemp.access.CostCenterScope;
import com.finemp.access.UserAccount;
import com.finemp.access.UserAccountRepository;
import com.finemp.accounting.classification.AccountingAccountClassificationService;
import com.finemp.accounting.source.AccountingSource;
import com.finemp.accounting.source.AccountingSourceRepository;
import com.finemp.accounting.source.AccountingSourceType;
import com.finemp.company.Company;
import com.finemp.company.CompanyRepository;
import com.finemp.integration.questor.QuestorAccountingImportService;
import com.finemp.integration.questor.QuestorAccountingReader;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "finemp.integration.questor.enabled=true")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AccountingMutationPermissionHttpIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private UserAccountRepository users;
    @Autowired private CompanyRepository companies;
    @Autowired private AccountingSourceRepository sources;
    @Autowired private CompanyAccessGrantRepository grants;
    @MockitoBean private QuestorAccountingImportService imports;
    @MockitoBean private QuestorAccountingReader reader;
    @MockitoBean private AccountingAccountClassificationService classifications;

    @Test
    void shouldRequireAuthenticationEvenWithCsrf() throws Exception {
        mvc.perform(post("/api/accounting-sources/{id}/questor-import", UUID.randomUUID()).with(csrf())
                        .param("startDate", "2026-07-01").param("endDate", "2026-07-31"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(imports, reader);
    }

    @Test
    void shouldNotTreatViewPermissionAsClassificationOrSyncPermission() throws Exception {
        var company = company("66666666000166");
        var source = source(company);
        var actor = actor("mutation-view@example.invalid", company, CostCenterScope.ALL, Set.of(CompanyPermission.DRE_VIEW));

        sync(actor, source.getId(), 403);
        classify(actor, company.getId(), UUID.randomUUID(), UUID.randomUUID(), 403);
        verifyNoInteractions(imports, reader, classifications);
    }

    @Test
    void shouldAllowExplicitWritesOnlyForTheCompanyResolvedFromTheSource() throws Exception {
        var company = company("77777777000177");
        var source = source(company);
        var foreign = source(company("88888888000188"));
        var actor = actor("mutation-editor@example.invalid", company, CostCenterScope.ALL,
                Set.of(CompanyPermission.QUESTOR_SYNC, CompanyPermission.ACCOUNT_CLASSIFICATION_MANAGE));
        UUID accountId = UUID.randomUUID();
        UUID classificationId = UUID.randomUUID();

        sync(actor, source.getId(), 204);
        sync(actor, foreign.getId(), 403);
        sync(actor, UUID.randomUUID(), 403);
        classify(actor, company.getId(), accountId, classificationId, 204);
        mvc.perform(post("/api/accounting-sources/{id}/questor-import", source.getId())
                        .with(user(new UserAccountPrincipal(actor)))
                        .param("startDate", "2026-07-01").param("endDate", "2026-07-31"))
                .andExpect(status().isForbidden());
        verify(imports).importMovements(source.getId(), LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
        verify(classifications).classifyAccount(company.getId(), accountId, classificationId);
        verifyNoInteractions(reader);
    }

    @Test
    void shouldNotImportOrClassifyUnderUnsupportedRestrictedScope() throws Exception {
        var company = company("99999999000199");
        var source = source(company);
        var actor = actor("mutation-restricted@example.invalid", company, CostCenterScope.RESTRICTED,
                Set.of(CompanyPermission.QUESTOR_SYNC, CompanyPermission.ACCOUNT_CLASSIFICATION_MANAGE));

        sync(actor, source.getId(), 403);
        classify(actor, company.getId(), UUID.randomUUID(), UUID.randomUUID(), 403);
        verifyNoInteractions(imports, reader, classifications);
    }

    private Company company(String cnpj) { return companies.saveAndFlush(new Company("Synthetic Mutation", cnpj)); }

    private AccountingSource source(Company company) {
        return sources.saveAndFlush(new AccountingSource(company, AccountingSourceType.QUESTOR, "synthetic-reference", true));
    }

    private UserAccount actor(String email, Company company, CostCenterScope scope, Set<CompanyPermission> permissions) {
        var actor = users.saveAndFlush(new UserAccount("Synthetic Actor", email, "synthetic-hash"));
        grants.saveAndFlush(new CompanyAccessGrant(actor, company, CompanyAccessRole.CUSTOM, scope, permissions));
        return actor;
    }

    private void sync(UserAccount actor, UUID sourceId, int expected) throws Exception {
        mvc.perform(post("/api/accounting-sources/{id}/questor-import", sourceId)
                        .with(user(new UserAccountPrincipal(actor))).with(csrf())
                        .param("startDate", "2026-07-01").param("endDate", "2026-07-31"))
                .andExpect(status().is(expected));
    }

    private void classify(UserAccount actor, UUID companyId, UUID accountId, UUID classificationId, int expected) throws Exception {
        mvc.perform(put("/api/companies/{companyId}/accounting-accounts/{accountId}/classification", companyId, accountId)
                        .with(user(new UserAccountPrincipal(actor))).with(csrf()).contentType(APPLICATION_JSON)
                        .content("{\"classificationId\":\"" + classificationId + "\"}"))
                .andExpect(status().is(expected));
    }
}
