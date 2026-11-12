package com.finemp.finance.reporting;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.finemp.TestcontainersConfiguration;
import com.finemp.access.*;
import com.finemp.accountingfirm.*;
import com.finemp.client.*;
import com.finemp.company.*;
import com.finemp.finance.bank.*;
import com.finemp.finance.category.*;
import com.finemp.finance.entry.*;
import com.finemp.finance.transfer.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import com.finemp.security.UserAccountPrincipal;

@SpringBootTest(properties={
        "finemp.finance.account-balances.enabled=true",
        "finemp.finance.catalog.enabled=true",
        "finemp.finance.entries.enabled=true",
        "finemp.finance.transfers.enabled=true"
})
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class BankAccountBalanceReadIntegrationTest {
    @Autowired BankAccountBalanceReadService reports;
    @Autowired BankAccountRepository banks;
    @Autowired FinancialCategoryRepository categories;
    @Autowired FinancialEntryRepository entries;
    @Autowired BankTransferRepository transfers;
    @Autowired CompanyRepository companies;
    @Autowired ClientRepository clients;
    @Autowired AccountingFirmRepository firms;
    @Autowired UserAccountRepository users;
    @Autowired CompanyAccessGrantRepository grants;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    Company owner,other;
    UserAccount allowed,bankOnly,entryOnly,restricted;
    UUID bankA,bankB,inactiveBank,foreignBank,incomeCategory,expenseCategory;
    final List<UUID> firmIds=new ArrayList<>(),clientIds=new ArrayList<>(),userIds=new ArrayList<>();

    @BeforeEach
    void prepare() {
        owner=company("Owner","31313131000131");
        other=company("Other","32323232000132");
        allowed=user("Allowed");
        bankOnly=user("Bank only");
        entryOnly=user("Entry only");
        restricted=user("Restricted");

        grant(allowed,CostCenterScope.ALL,Set.of(
                CompanyPermission.BANK_ACCOUNT_VIEW,
                CompanyPermission.FINANCIAL_ENTRY_VIEW));
        grant(bankOnly,CostCenterScope.ALL,Set.of(CompanyPermission.BANK_ACCOUNT_VIEW));
        grant(entryOnly,CostCenterScope.ALL,Set.of(CompanyPermission.FINANCIAL_ENTRY_VIEW));
        grant(restricted,CostCenterScope.RESTRICTED,Set.of(
                CompanyPermission.BANK_ACCOUNT_VIEW,
                CompanyPermission.FINANCIAL_ENTRY_VIEW));

        bankA=bank(owner,"Conta A","100.00",true);
        bankB=bank(owner,"Conta B","-20.00",true);
        inactiveBank=bank(owner,"Conta inativa","7.00",false);
        foreignBank=bank(other,"Conta estrangeira","999.00",true);

        incomeCategory=category(owner,FinancialDirection.INCOME,"Receitas");
        expenseCategory=category(owner,FinancialDirection.EXPENSE,"Despesas");
    }

    @AfterEach
    void cleanSyntheticFixtures() {
        for(var company:List.of(owner,other)) {
            jdbc.update("DELETE FROM financial_bank_transfers WHERE company_id=?",company.getId());
            jdbc.update("DELETE FROM financial_entries WHERE company_id=?",company.getId());
            jdbc.update("DELETE FROM financial_categories WHERE company_id=?",company.getId());
            jdbc.update("DELETE FROM financial_bank_accounts WHERE company_id=?",company.getId());
            jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id IN (SELECT id FROM company_access_grants WHERE company_id=?)",company.getId());
            jdbc.update("DELETE FROM company_access_grants WHERE company_id=?",company.getId());
            jdbc.update("DELETE FROM companies WHERE id=?",company.getId());
        }
        for(var id:clientIds)jdbc.update("DELETE FROM clients WHERE id=?",id);
        for(var id:firmIds)jdbc.update("DELETE FROM accounting_firms WHERE id=?",id);
        for(var id:userIds)jdbc.update("DELETE FROM user_accounts WHERE id=?",id);
    }

    @Test
    void shouldExposeTheAuthenticatedHttpRouteAndKeepServiceAuthorization() throws Exception {
        saveEntry(owner,bankA,incomeCategory,FinancialDirection.INCOME,"5.00",null,LocalDate.of(2026,7,1));

        mvc.perform(get("/api/companies/"+owner.getId()+"/financial-reports/account-balances")
                        .with(authentication(new UsernamePasswordAuthenticationToken(new UserAccountPrincipal(allowed),null,List.of()))))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control",org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.accounts[0].id").value(bankA.toString()))
                .andExpect(jsonPath("$.accounts[0].openingBalance").value("100.00"))
                .andExpect(jsonPath("$.accounts[0].currentBalance").value("105.00"));

        mvc.perform(get("/api/companies/"+owner.getId()+"/financial-reports/account-balances")
                        .with(authentication(new UsernamePasswordAuthenticationToken(new UserAccountPrincipal(bankOnly),null,List.of()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldDeriveExactBalanceFromOpeningRealizedEntriesAndTransfers() {
        saveEntry(owner,bankA,incomeCategory,FinancialDirection.INCOME,"50.00","55.25",LocalDate.of(2026,6,30));
        saveEntry(owner,bankA,expenseCategory,FinancialDirection.EXPENSE,"10.10",null,LocalDate.of(2026,7,2));
        saveEntry(owner,bankA,incomeCategory,FinancialDirection.INCOME,"999.99",null,null);
        transfers.saveAndFlush(new BankTransfer(owner,
                new BankTransferValues(bankA,bankB,LocalDate.of(2026,7,3),new BigDecimal("12.34"),"Synthetic transfer"),
                allowed.getId()));

        var report=reports.report(allowed.getId(),owner.getId());
        var a=account(report,bankA);
        var b=account(report,bankB);

        assertThat(a.openingBalance()).isEqualByComparingTo("100.00");
        assertThat(a.currentBalance()).isEqualByComparingTo("132.81");
        assertThat(b.openingBalance()).isEqualByComparingTo("-20.00");
        assertThat(b.currentBalance()).isEqualByComparingTo("-7.66");
    }

    @Test
    void shouldKeepInactiveAccountsAndIgnoreOpeningDateAsAFilter() {
        saveEntry(owner,inactiveBank,incomeCategory,FinancialDirection.INCOME,"3.00",null,LocalDate.of(2025,1,1));

        var row=account(reports.report(allowed.getId(),owner.getId()),inactiveBank);
        assertThat(row.active()).isFalse();
        assertThat(row.currentBalance()).isEqualByComparingTo("10.00");
    }

    @Test
    void shouldKeepCompanyIsolationAndStableNameOrdering() {
        var report=reports.report(allowed.getId(),owner.getId());
        assertThat(report.accounts()).extracting(BankAccountBalanceReadService.AccountBalance::id)
                .containsExactly(bankA,bankB,inactiveBank)
                .doesNotContain(foreignBank);
    }

    @Test
    void shouldRequireBothBankAndFinancialEntryPermissionWithAllScope() {
        assertThatThrownBy(()->reports.report(bankOnly.getId(),owner.getId()))
                .isInstanceOf(com.finemp.administration.OrganizationAccessDeniedException.class);
        assertThatThrownBy(()->reports.report(entryOnly.getId(),owner.getId()))
                .isInstanceOf(com.finemp.administration.OrganizationAccessDeniedException.class);
        assertThatThrownBy(()->reports.report(restricted.getId(),owner.getId()))
                .isInstanceOf(com.finemp.administration.OrganizationAccessDeniedException.class);

        assertThat(reports.report(allowed.getId(),owner.getId()).accounts()).hasSize(3);

        jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id IN (SELECT id FROM company_access_grants WHERE company_id=? AND user_account_id=?) AND permission='FINANCIAL_ENTRY_VIEW'",
                owner.getId(),allowed.getId());

        assertThatThrownBy(()->reports.report(allowed.getId(),owner.getId()))
                .isInstanceOf(com.finemp.administration.OrganizationAccessDeniedException.class);
    }

    private BankAccountBalanceReadService.AccountBalance account(BankAccountBalanceReadService.Report report,UUID id) {
        return report.accounts().stream().filter(account->account.id().equals(id)).findFirst().orElseThrow();
    }

    private void saveEntry(Company company,UUID bank,UUID category,FinancialDirection direction,String nominal,String actual,LocalDate paymentDate) {
        var date=LocalDate.of(2026,7,1);
        var values=new FinancialEntryValues(
                direction,
                "Synthetic balance entry",
                new BigDecimal(nominal),
                date,
                date,
                YearMonth.of(2026,7),
                date,
                paymentDate,
                actual==null?null:new BigDecimal(actual),
                bank,
                category,
                null,
                null,
                null,
                "",
                "SYNTHETIC",
                "");
        entries.saveAndFlush(new FinancialEntry(company,values,allowed.getId()));
    }

    private UUID bank(Company company,String name,String opening,boolean active) {
        return banks.saveAndFlush(new BankAccount(
                company,name,BankAccountType.CASH,new BigDecimal(opening),LocalDate.of(2026,7,1),active)).getId();
    }

    private UUID category(Company company,FinancialDirection direction,String label) {
        var group=categories.saveAndFlush(FinancialCategory.group(company,direction,1,"Synthetic "+label));
        return categories.saveAndFlush(FinancialCategory.subcategory(group,1,"Synthetic child "+label)).getId();
    }

    private Company company(String label,String cnpj) {
        var firm=firms.saveAndFlush(new AccountingFirm("Synthetic Balance Firm "+label,true));
        firmIds.add(firm.getId());
        var client=clients.saveAndFlush(new Client(firm,"Synthetic Balance Client "+label,true));
        clientIds.add(client.getId());
        return companies.saveAndFlush(new Company(client,"Synthetic Balance Company "+label,cnpj));
    }

    private UserAccount user(String label) {
        var user=users.saveAndFlush(new UserAccount("Synthetic Balance "+label,UUID.randomUUID()+"@example.invalid","hash"));
        userIds.add(user.getId());
        return user;
    }

    private void grant(UserAccount user,CostCenterScope scope,Set<CompanyPermission> permissions) {
        grants.saveAndFlush(new CompanyAccessGrant(user,owner,CompanyAccessRole.CUSTOM,scope,permissions));
    }
}
