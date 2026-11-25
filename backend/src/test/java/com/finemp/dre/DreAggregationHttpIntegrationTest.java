package com.finemp.dre;

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
import com.finemp.accounting.classification.AccountingAccountClassification;
import com.finemp.accounting.classification.AccountingAccountClassificationRepository;
import com.finemp.accounting.classification.DreClassification;
import com.finemp.accounting.classification.DreClassificationRepository;
import com.finemp.accounting.movement.AccountingMovement;
import com.finemp.accounting.movement.AccountingMovementRepository;
import com.finemp.accounting.movement.AccountingMovementSide;
import com.finemp.accounting.canonical.v2.AccountingDataset;
import com.finemp.accounting.coverage.AccountingDatasetCoverageService;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * M6 (#446): DRE contábil sobre PostgreSQL, com a regra de sinal existente,
 * múltiplas fontes, exclusão de não classificadas, agregação autoritativa e cobertura.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class DreAggregationHttpIntegrationTest {

    private static final String ROUTE = "/api/companies/{companyId}/dre";

    @Autowired private MockMvc mvc;
    @Autowired private UserAccountRepository users;
    @Autowired private CompanyRepository companies;
    @Autowired private AccountingSourceRepository sources;
    @Autowired private AccountingAccountRepository accounts;
    @Autowired private AccountingMovementRepository movements;
    @Autowired private DreClassificationRepository catalog;
    @Autowired private AccountingAccountClassificationRepository classifications;
    @Autowired private CompanyAccessGrantRepository grants;
    @Autowired private AccountingDatasetCoverageService coverage;

    private Company company;
    private AccountingSource sourceA;
    private AccountingSource sourceB;
    private UserAccount viewer;

    @BeforeEach
    void prepare() {
        company = companies.saveAndFlush(new Company("Synthetic DRE Company", "61616161000161"));
        Company other = companies.saveAndFlush(new Company("Synthetic DRE Other", "62626262000162"));
        sourceA = sources.saveAndFlush(new AccountingSource(company, AccountingSourceType.QUESTOR, "dre-a", true));
        sourceB = sources.saveAndFlush(new AccountingSource(company, AccountingSourceType.QUESTOR, "dre-b", true));
        AccountingSource foreign = sources.saveAndFlush(new AccountingSource(other, AccountingSourceType.QUESTOR, "dre-x", true));

        DreClassification services = catalog.saveAndFlush(new DreClassification("Z Synthetic EBITDA", "Receita bruta", "Serviços"));
        DreClassification goods = catalog.saveAndFlush(new DreClassification("Z Synthetic EBITDA", "Receita bruta", "Mercadorias"));
        DreClassification energy = catalog.saveAndFlush(new DreClassification("Z Synthetic EBITDA", "Despesas", "Energia"));
        DreClassification financial = catalog.saveAndFlush(new DreClassification("A Synthetic Financeiro", "Financeiras", "Juros"));

        // Receita (conta 3.*): crédito positivo, débito negativo.
        AccountingAccount revenueA = classified(sourceA, "3.1.01.001", services);
        AccountingAccount revenueB = classified(sourceB, "3.1.01.001", services);
        AccountingAccount goodsRevenue = classified(sourceA, "3.1.01.002", goods);
        // Despesa (conta 4.*): débito negativo.
        AccountingAccount energyExpense = classified(sourceA, "4.1.01.001", energy);
        // Conta iniciada por "1" classificada na DRE: débito positivo (regra existente).
        AccountingAccount interest = classified(sourceA, "1.9.01.001", financial);
        AccountingAccount pending = accounts.saveAndFlush(new AccountingAccount(sourceA, "x1", "3.9.99.001", "Sem classificação"));
        AccountingAccount foreignRevenue = classified(foreign, "3.1.01.001", services);

        movement(revenueA, "R1", AccountingMovementSide.CREDIT, "1000.0000");
        movement(revenueA, "R2", AccountingMovementSide.DEBIT, "100.0000");
        movement(revenueB, "R1", AccountingMovementSide.CREDIT, "500.5000");
        movement(goodsRevenue, "G1", AccountingMovementSide.CREDIT, "0.0001");
        movement(energyExpense, "E1", AccountingMovementSide.DEBIT, "300.0000");
        movement(interest, "J1", AccountingMovementSide.DEBIT, "10.0000");
        movement(interest, "J2", AccountingMovementSide.CREDIT, "2.0000");
        movement(pending, "P1", AccountingMovementSide.CREDIT, "777.0000");
        movement(foreignRevenue, "X1", AccountingMovementSide.CREDIT, "9999.0000");

        coverage.record(
                sourceA.getId(),
                AccountingDataset.MOVEMENTS,
                LocalDate.parse("2026-01-01"),
                LocalDate.parse("2026-03-31"));
        coverage.record(
                sourceA.getId(),
                AccountingDataset.MOVEMENTS,
                LocalDate.parse("2026-05-01"),
                LocalDate.parse("2026-06-30"));

        viewer = users.saveAndFlush(new UserAccount("Synthetic DRE Viewer", UUID.randomUUID() + "@example.invalid", "hash"));
        grants.saveAndFlush(new CompanyAccessGrant(viewer, company, CompanyAccessRole.CUSTOM, CostCenterScope.ALL,
                Set.of(CompanyPermission.DRE_VIEW)));
    }

    @Test
    void appliesExistingSignRuleAcrossSourcesWithoutDoubleCountingOrForeignData() throws Exception {
        mvc.perform(june().with(user(new UserAccountPrincipal(viewer))))
                .andExpect(status().isOk())
                // Ordem técnica estável: DRE, DRE ABERTA, EBITDA.
                .andExpect(jsonPath("$.lines[*].ebitdaClassification", contains("Juros", "Energia", "Mercadorias", "Serviços")))
                .andExpect(jsonPath("$.lines[0].total").value(8.0))
                .andExpect(jsonPath("$.lines[1].total").value(-300.0))
                // Serviços: 1000 - 100 (fonte A) + 500.5 (fonte B); a outra Company não entra.
                .andExpect(jsonPath("$.lines[3].total").value(1400.5))
                .andExpect(jsonPath("$.unclassifiedAccountCount").value(1));
    }

    @Test
    void aggregatesGroupsAuthoritativelyWithExactDecimals() throws Exception {
        mvc.perform(june().with(user(new UserAccountPrincipal(viewer))))
                .andExpect(jsonPath("$.groups", hasSize(2)))
                .andExpect(jsonPath("$.groups[0].dreClassification").value("A Synthetic Financeiro"))
                .andExpect(jsonPath("$.groups[0].total").value("8.0000"))
                .andExpect(jsonPath("$.groups[1].dreClassification").value("Z Synthetic EBITDA"))
                .andExpect(jsonPath("$.groups[1].total").value("1100.5001"))
                .andExpect(jsonPath("$.groups[1].groups[*].dreOpenClassification", contains("Despesas", "Receita bruta")))
                .andExpect(jsonPath("$.groups[1].groups[1].total").value("1400.5001"))
                .andExpect(jsonPath("$.groups[1].groups[1].lines[0].ebitdaClassification").value("Mercadorias"))
                .andExpect(jsonPath("$.groups[1].groups[1].lines[0].total").value("0.0001"))
                .andExpect(jsonPath("$.classifiedTotal").value("1108.5001"));
    }

    @Test
    void reportsFactualCoveragePerSource() throws Exception {
        String first = sourceA.getId().toString().compareTo(sourceB.getId().toString()) < 0 ? "A" : "B";
        int indexA = first.equals("A") ? 0 : 1;
        int indexB = 1 - indexA;
        mvc.perform(june().with(user(new UserAccountPrincipal(viewer))))
                .andExpect(jsonPath("$.coverage", hasSize(2)))
                .andExpect(jsonPath("$.coverage[" + indexA + "].accountingSourceId").value(sourceA.getId().toString()))
                .andExpect(jsonPath("$.coverage[" + indexA + "].importedFrom").value("2026-01-01"))
                .andExpect(jsonPath("$.coverage[" + indexA + "].importedTo").value("2026-06-30"))
                .andExpect(jsonPath("$.coverage[" + indexA + "].intervals", hasSize(2)))
                .andExpect(jsonPath("$.coverage[" + indexA + "].intervals[0].importedFrom").value("2026-01-01"))
                .andExpect(jsonPath("$.coverage[" + indexA + "].intervals[0].importedTo").value("2026-03-31"))
                .andExpect(jsonPath("$.coverage[" + indexA + "].intervals[1].importedFrom").value("2026-05-01"))
                .andExpect(jsonPath("$.coverage[" + indexA + "].intervals[1].importedTo").value("2026-06-30"))
                .andExpect(jsonPath("$.coverage[" + indexB + "].importedFrom").isEmpty())
                .andExpect(jsonPath("$.coverage[" + indexB + "].intervals", hasSize(0)));
    }

    @Test
    void invertedPeriodIsABadRequest() throws Exception {
        mvc.perform(get(ROUTE, company.getId()).param("startDate", "2026-06-30").param("endDate", "2026-06-01")
                        .with(user(new UserAccountPrincipal(viewer))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void negativeStoredAmountIsReportedExplicitlyInsteadOfServerError() throws Exception {
        // #449: o canônico preserva negativos; a semântica na DRE é DECISÃO DE PRODUTO pendente.
        AccountingAccount revenue = accounts.findAll().stream()
                .filter(account -> account.getAccountingSource().getId().equals(sourceA.getId()))
                .filter(account -> account.getAccountKey().equals("3.1.01.001"))
                .findFirst().orElseThrow();
        movement(revenue, "NEG", AccountingMovementSide.CREDIT, "-1.0000");

        mvc.perform(june().with(user(new UserAccountPrincipal(viewer))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DRE_UNSUPPORTED_NEGATIVE_AMOUNT"));
    }

    private MockHttpServletRequestBuilder june() {
        return get(ROUTE, company.getId()).param("startDate", "2026-06-01").param("endDate", "2026-06-30");
    }

    private AccountingAccount classified(AccountingSource source, String key, DreClassification classification) {
        AccountingAccount account = accounts.saveAndFlush(
                new AccountingAccount(source, UUID.randomUUID().toString(), key, "Synthetic " + key));
        classifications.saveAndFlush(new AccountingAccountClassification(account, classification));
        return account;
    }

    private void movement(AccountingAccount account, String entry, AccountingMovementSide side, String amount) {
        movements.saveAndFlush(new AccountingMovement(account, entry, side, LocalDate.parse("2026-06-15"),
                new BigDecimal(amount), "Synthetic " + entry));
    }
}
