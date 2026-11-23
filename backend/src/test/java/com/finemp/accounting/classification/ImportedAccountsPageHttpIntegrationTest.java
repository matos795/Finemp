package com.finemp.accounting.classification;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.finemp.accounting.account.AccountingAccount;
import com.finemp.accounting.account.AccountingAccountRepository;
import com.finemp.accounting.source.AccountingSource;
import com.finemp.accounting.source.AccountingSourceRepository;
import com.finemp.accounting.source.AccountingSourceType;
import com.finemp.company.Company;
import com.finemp.company.CompanyRepository;
import com.finemp.security.UserAccountPrincipal;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/**
 * M4 (#442): contas importadas da Company, paginadas e filtradas no servidor,
 * com contagem de pendências. O universo é o importado, não o plano completo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class ImportedAccountsPageHttpIntegrationTest {

    private static final String ROUTE = "/api/companies/{companyId}/accounting-accounts";

    @Autowired private MockMvc mvc;
    @Autowired private UserAccountRepository users;
    @Autowired private CompanyRepository companies;
    @Autowired private AccountingSourceRepository sources;
    @Autowired private AccountingAccountRepository accounts;
    @Autowired private DreClassificationRepository catalog;
    @Autowired private AccountingAccountClassificationRepository classifications;
    @Autowired private CompanyAccessGrantRepository grants;

    private Company company;
    private Company otherCompany;
    private AccountingSource sourceA;
    private AccountingSource sourceB;
    private AccountingSource foreignSource;
    private UserAccount viewer;

    @BeforeEach
    void prepare() {
        company = companies.saveAndFlush(new Company("Synthetic Accounts Company", "41414141000141"));
        otherCompany = companies.saveAndFlush(new Company("Synthetic Foreign Company", "42424242000142"));
        sourceA = sources.saveAndFlush(new AccountingSource(company, AccountingSourceType.QUESTOR, "acc-a", true));
        sourceB = sources.saveAndFlush(new AccountingSource(company, AccountingSourceType.QUESTOR, "acc-b", true));
        foreignSource = sources.saveAndFlush(new AccountingSource(otherCompany, AccountingSourceType.QUESTOR, "acc-x", true));

        DreClassification revenue = catalog.saveAndFlush(
                new DreClassification("Synthetic Revenue", "Synthetic Revenue Open", "Synthetic EBITDA"));

        AccountingAccount cash = account(sourceA, "1.1.01.001", "Caixa geral");
        account(sourceA, "1.1.02.001", "Banco sintético");
        AccountingAccount sales = account(sourceA, "3.1.01.001", "Receita de serviços");
        // Mesmo código em outra fonte da mesma Company: desempate determinístico por fonte/id.
        account(sourceB, "1.1.01.001", "Caixa filial");
        account(foreignSource, "1.1.01.001", "Caixa de outra empresa");

        classifications.saveAndFlush(new AccountingAccountClassification(sales, revenue));
        classifications.saveAndFlush(new AccountingAccountClassification(cash, revenue));

        viewer = account("Synthetic Accounts Viewer");
        grant(viewer, company, CompanyPermission.ACCOUNT_CLASSIFICATION_VIEW);
    }

    @Test
    void requiresSessionAndClassificationPermissionOnThatCompany() throws Exception {
        mvc.perform(get(ROUTE, company.getId())).andExpect(status().isUnauthorized());
        mvc.perform(get(ROUTE, otherCompany.getId()).with(as(viewer))).andExpect(status().isForbidden());

        UserAccount ledgerOnly = account("Synthetic Ledger Only");
        grant(ledgerOnly, company, CompanyPermission.ACCOUNTING_MOVEMENT_VIEW);
        mvc.perform(get(ROUTE, company.getId()).with(as(ledgerOnly))).andExpect(status().isForbidden());

        UserAccount manager = account("Synthetic Classification Manager");
        grant(manager, company, CompanyPermission.ACCOUNT_CLASSIFICATION_MANAGE);
        mvc.perform(get(ROUTE, company.getId()).with(as(manager))).andExpect(status().isOk());
    }

    @Test
    void listsOnlyThisCompanyInDeterministicOrderWithSummaryAndUniverse() throws Exception {
        mvc.perform(get(ROUTE, company.getId()).with(as(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.universe").value("IMPORTED_ACCOUNTS"))
                .andExpect(jsonPath("$.items", hasSize(4)))
                .andExpect(jsonPath("$.items[*].accountKey",
                        contains("1.1.01.001", "1.1.01.001", "1.1.02.001", "3.1.01.001")))
                .andExpect(jsonPath("$.items[0].description").value(
                        sourceA.getId().toString().compareTo(sourceB.getId().toString()) < 0 ? "Caixa geral" : "Caixa filial"))
                .andExpect(jsonPath("$.totalItems").value(4))
                .andExpect(jsonPath("$.summary.total").value(4))
                .andExpect(jsonPath("$.summary.classified").value(2))
                .andExpect(jsonPath("$.summary.pending").value(2));
    }

    @Test
    void filtersBySearchStatusAndSourceAndPagesStably() throws Exception {
        mvc.perform(get(ROUTE, company.getId()).with(as(viewer)).param("search", "CAIXA"))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.summary.total").value(2));

        mvc.perform(get(ROUTE, company.getId()).with(as(viewer)).param("search", "3.1.01"))
                .andExpect(jsonPath("$.items[*].description", contains("Receita de serviços")));

        mvc.perform(get(ROUTE, company.getId()).with(as(viewer)).param("status", "PENDING"))
                .andExpect(jsonPath("$.items[*].status", contains("PENDING", "PENDING")))
                // Contagens refletem fonte/busca, não o filtro de status nem a página.
                .andExpect(jsonPath("$.summary.total").value(4))
                .andExpect(jsonPath("$.summary.pending").value(2));

        mvc.perform(get(ROUTE, company.getId()).with(as(viewer)).param("accountingSourceId", sourceB.getId().toString()))
                .andExpect(jsonPath("$.items[*].description", contains("Caixa filial")))
                .andExpect(jsonPath("$.summary.total").value(1));

        mvc.perform(get(ROUTE, company.getId()).with(as(viewer)).param("size", "3").param("page", "1"))
                .andExpect(jsonPath("$.items[*].accountKey", contains("3.1.01.001")))
                .andExpect(jsonPath("$.totalItems").value(4))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.summary.classified").value(2));
    }

    @Test
    void foreignSourceFilterNeverLeaksAnotherCompany() throws Exception {
        mvc.perform(get(ROUTE, company.getId()).with(as(viewer))
                        .param("accountingSourceId", foreignSource.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.summary.total").value(0));
    }

    @Test
    void rejectsInvalidPagingAndStatus() throws Exception {
        mvc.perform(get(ROUTE, company.getId()).with(as(viewer)).param("size", "101")).andExpect(status().isBadRequest());
        mvc.perform(get(ROUTE, company.getId()).with(as(viewer)).param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get(ROUTE, company.getId()).with(as(viewer)).param("status", "UNKNOWN")).andExpect(status().isBadRequest());
    }

    private AccountingAccount account(AccountingSource source, String key, String description) {
        return accounts.saveAndFlush(new AccountingAccount(source, UUID.randomUUID().toString(), key, description));
    }

    private UserAccount account(String name) {
        return users.saveAndFlush(new UserAccount(name, UUID.randomUUID() + "@example.invalid", "hash"));
    }

    private void grant(UserAccount account, Company target, CompanyPermission permission) {
        grants.saveAndFlush(new CompanyAccessGrant(account, target, CompanyAccessRole.CUSTOM, CostCenterScope.ALL,
                Set.of(permission)));
    }

    private static RequestPostProcessor as(UserAccount account) {
        return user(new UserAccountPrincipal(account));
    }
}
