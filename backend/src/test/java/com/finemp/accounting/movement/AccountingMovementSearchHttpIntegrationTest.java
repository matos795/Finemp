package com.finemp.accounting.movement;

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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/** M5 (#444): movimentos paginados, filtrados e com totais exatos no servidor. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AccountingMovementSearchHttpIntegrationTest {

    private static final String ROUTE = "/api/companies/{companyId}/accounting-movements/search";

    @Autowired private MockMvc mvc;
    @Autowired private UserAccountRepository users;
    @Autowired private CompanyRepository companies;
    @Autowired private AccountingSourceRepository sources;
    @Autowired private AccountingAccountRepository accounts;
    @Autowired private AccountingMovementRepository movements;
    @Autowired private CompanyAccessGrantRepository grants;

    private Company company;
    private Company otherCompany;
    private AccountingSource sourceA;
    private AccountingSource sourceB;
    private AccountingSource foreignSource;
    private AccountingAccount cash;
    private AccountingAccount revenue;
    private AccountingAccount foreignAccount;
    private UserAccount reader;

    @BeforeEach
    void prepare() {
        company = companies.saveAndFlush(new Company("Synthetic Ledger Company", "51515151000151"));
        otherCompany = companies.saveAndFlush(new Company("Synthetic Ledger Foreign", "52525252000152"));
        sourceA = sources.saveAndFlush(new AccountingSource(company, AccountingSourceType.QUESTOR, "led-a", true));
        sourceB = sources.saveAndFlush(new AccountingSource(company, AccountingSourceType.QUESTOR, "led-b", true));
        foreignSource = sources.saveAndFlush(new AccountingSource(otherCompany, AccountingSourceType.QUESTOR, "led-x", true));

        cash = accounts.saveAndFlush(new AccountingAccount(sourceA, "10", "1.1.01.001", "Caixa"));
        revenue = accounts.saveAndFlush(new AccountingAccount(sourceA, "30", "3.1.01.001", "Receita"));
        AccountingAccount branchCash = accounts.saveAndFlush(new AccountingAccount(sourceB, "10", "1.1.01.001", "Caixa filial"));
        foreignAccount = accounts.saveAndFlush(new AccountingAccount(foreignSource, "10", "1.1.01.001", "Caixa estrangeiro"));

        // Mesma data em várias linhas: a ordem precisa de desempate determinístico.
        movement(cash, "L2", AccountingMovementSide.DEBIT, "2026-03-10", "100.0001");
        movement(revenue, "L2", AccountingMovementSide.CREDIT, "2026-03-10", "100.0001");
        movement(cash, "L1", AccountingMovementSide.DEBIT, "2026-03-10", "0.1000");
        movement(revenue, "L1", AccountingMovementSide.CREDIT, "2026-03-10", "0.1000");
        movement(branchCash, "F1", AccountingMovementSide.DEBIT, "2026-03-05", "50.0000");
        movement(cash, "OUT", AccountingMovementSide.DEBIT, "2026-04-01", "999.0000");
        movement(foreignAccount, "X1", AccountingMovementSide.DEBIT, "2026-03-10", "7.0000");

        reader = users.saveAndFlush(new UserAccount("Synthetic Ledger Reader", UUID.randomUUID() + "@example.invalid", "hash"));
        grant(reader, company, CompanyPermission.ACCOUNTING_MOVEMENT_VIEW);
    }

    @Test
    void requiresSessionAndLedgerPermissionOnThatCompany() throws Exception {
        mvc.perform(march()).andExpect(status().isUnauthorized());
        mvc.perform(get(ROUTE, otherCompany.getId()).param("startDate", "2026-03-01").param("endDate", "2026-03-31")
                .with(as(reader))).andExpect(status().isForbidden());

        UserAccount dreOnly = users.saveAndFlush(new UserAccount("Synthetic DRE Only", UUID.randomUUID() + "@example.invalid", "hash"));
        grant(dreOnly, company, CompanyPermission.DRE_VIEW);
        mvc.perform(march().with(as(dreOnly))).andExpect(status().isForbidden());
    }

    @Test
    void ordersDeterministicallyWithOriginAndExactAmounts() throws Exception {
        String firstSourceEntry = "L1";
        mvc.perform(march().with(as(reader)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(5)))
                .andExpect(jsonPath("$.items[0].sourceEntryKey").value("F1"))
                .andExpect(jsonPath("$.items[0].accountingSourceId").value(sourceB.getId().toString()))
                .andExpect(jsonPath("$.items[1].sourceEntryKey").value(firstSourceEntry))
                .andExpect(jsonPath("$.items[1].side").value("CREDIT"))
                .andExpect(jsonPath("$.items[2].sourceEntryKey").value(firstSourceEntry))
                .andExpect(jsonPath("$.items[2].side").value("DEBIT"))
                .andExpect(jsonPath("$.items[2].accountId").value(cash.getId().toString()))
                .andExpect(jsonPath("$.items[2].accountKey").value("1.1.01.001"))
                .andExpect(jsonPath("$.items[2].amount").value("0.1000"))
                .andExpect(jsonPath("$.items[3].amount").value("100.0001"))
                .andExpect(jsonPath("$.totals.debit").value("150.1001"))
                .andExpect(jsonPath("$.totals.credit").value("100.1001"))
                .andExpect(jsonPath("$.notSynchronized",
                        contains("COUNTERPART", "DOCUMENT", "COST_CENTER", "CLOSING_ENTRY", "OPENING_BALANCE")))
                .andExpect(jsonPath("$.items[0].counterpart").doesNotExist())
                .andExpect(jsonPath("$.items[0].document").doesNotExist());
    }

    @Test
    void filtersBySourceAndAccountAndPagesWithStableTotals() throws Exception {
        mvc.perform(march().with(as(reader)).param("accountingSourceId", sourceA.getId().toString()))
                .andExpect(jsonPath("$.items", hasSize(4)))
                .andExpect(jsonPath("$.totals.debit").value("100.1001"));

        mvc.perform(march().with(as(reader)).param("accountId", revenue.getId().toString()))
                .andExpect(jsonPath("$.items[*].side", contains("CREDIT", "CREDIT")))
                .andExpect(jsonPath("$.totals.debit").value("0.0000"))
                .andExpect(jsonPath("$.totals.credit").value("100.1001"));

        mvc.perform(march().with(as(reader)).param("size", "2").param("page", "2"))
                .andExpect(jsonPath("$.items[*].sourceEntryKey", contains("L2")))
                .andExpect(jsonPath("$.totalItems").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.totals.debit").value("150.1001"));
    }

    @Test
    void foreignSourceOrAccountNeverLeaks() throws Exception {
        mvc.perform(march().with(as(reader)).param("accountingSourceId", foreignSource.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.totals.debit").value("0.0000"));
        mvc.perform(march().with(as(reader)).param("accountId", foreignAccount.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void preservesSignedAmountsExactlyInItemsAndTotals() throws Exception {
        // O contrato canônico preserva valores negativos (CanonicalBatchValidatorTest).
        movement(cash, "NEG", AccountingMovementSide.DEBIT, "2026-03-20", "-200.2002");
        mvc.perform(march().with(as(reader)).param("accountId", cash.getId().toString()))
                .andExpect(jsonPath("$.items[*].amount", contains("0.1000", "100.0001", "-200.2002")))
                .andExpect(jsonPath("$.totals.debit").value("-100.1001"))
                .andExpect(jsonPath("$.totals.credit").value("0.0000"));
    }

    @Test
    void rejectsInvalidPeriodAndPaging() throws Exception {
        mvc.perform(get(ROUTE, company.getId()).param("startDate", "2026-04-01").param("endDate", "2026-03-01")
                .with(as(reader))).andExpect(status().isBadRequest());
        mvc.perform(get(ROUTE, company.getId()).with(as(reader))).andExpect(status().isBadRequest());
        mvc.perform(march().with(as(reader)).param("size", "201")).andExpect(status().isBadRequest());
        mvc.perform(march().with(as(reader)).param("page", "-1")).andExpect(status().isBadRequest());
    }

    private MockHttpServletRequestBuilder march() {
        return get(ROUTE, company.getId()).param("startDate", "2026-03-01").param("endDate", "2026-03-31");
    }

    private void movement(AccountingAccount account, String entry, AccountingMovementSide side, String date, String amount) {
        movements.saveAndFlush(new AccountingMovement(account, entry, side, LocalDate.parse(date), new BigDecimal(amount),
                "Synthetic history " + entry));
    }

    private void grant(UserAccount account, Company target, CompanyPermission permission) {
        grants.saveAndFlush(new CompanyAccessGrant(account, target, CompanyAccessRole.CUSTOM, CostCenterScope.ALL,
                Set.of(permission)));
    }

    private static RequestPostProcessor as(UserAccount account) {
        return user(new UserAccountPrincipal(account));
    }
}
