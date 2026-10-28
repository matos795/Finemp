package com.finemp.finance.entry;

import com.finemp.TestcontainersConfiguration;
import com.finemp.access.*;
import com.finemp.accountingfirm.*;
import com.finemp.client.*;
import com.finemp.company.*;
import com.finemp.finance.bank.*;
import com.finemp.finance.partner.*;
import com.finemp.finance.category.*;
import com.finemp.finance.center.*;
import com.finemp.security.UserAccountPrincipal;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"finemp.finance.entries.enabled=true","finemp.finance.catalog.enabled=true"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class FinancialEntryReadIntegrationTest {
    @Autowired FinancialEntryRepository entries;
    @Autowired FinancialEntryReadService service;
    @Autowired CompanyRepository companies;
    @Autowired ClientRepository clients;
    @Autowired AccountingFirmRepository firms;
    @Autowired UserAccountRepository users;
    @Autowired CompanyAccessGrantRepository grants;
    @Autowired BankAccountRepository banks;
    @Autowired BusinessPartnerRepository partners;
    @Autowired FinancialCategoryRepository categories;
    @Autowired ResultCenterRepository centers;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired PlatformTransactionManager transactions;
    private Company owner,other;
    private UserAccount viewer,manager,admin,restricted;
    private UUID bank,party,expenseGroup,expenseCategory,incomeCategory,centerRoot,centerLeaf,centerTenth,centerOther,openId,paidId,futureId,foreignId;
    private final List<UUID> firmIds=new ArrayList<>(),clientIds=new ArrayList<>(),userIds=new ArrayList<>();
    @BeforeEach void prepare() {
        owner=company("Owner","73737373000173");other=company("Other","74747474000174");
        viewer=createUser("Viewer");manager=createUser("Manager");admin=createUser("Admin");restricted=createUser("Restricted");
        grant(viewer,CompanyAccessRole.VIEWER,CostCenterScope.ALL,Set.of(CompanyPermission.FINANCIAL_ENTRY_VIEW));
        grant(manager,CompanyAccessRole.CUSTOM,CostCenterScope.ALL,Set.of(CompanyPermission.FINANCIAL_ENTRY_MANAGE));
        grant(admin,CompanyAccessRole.ADMIN,CostCenterScope.ALL,Set.of());
        grant(restricted,CompanyAccessRole.CUSTOM,CostCenterScope.RESTRICTED,Set.of(CompanyPermission.FINANCIAL_ENTRY_VIEW));
        bank=banks.saveAndFlush(new BankAccount(owner,"Banco A",BankAccountType.CASH,BigDecimal.ZERO,LocalDate.of(2026,1,1),true)).getId();
        party=partners.saveAndFlush(new BusinessPartner(owner,new BusinessPartnerProfile(PartnerPersonType.LEGAL_ENTITY,"12ABC34501DE35","Synthetic Party","Fantasia Alfa",PartnerRole.BOTH,
                "private-phone","private@synthetic.invalid","","","","","private-street","","","","","","","",true))).getId();
        var expense=categories.saveAndFlush(FinancialCategory.group(owner,FinancialDirection.EXPENSE,1,"Despesas"));expenseGroup=expense.getId();
        expenseCategory=categories.saveAndFlush(FinancialCategory.subcategory(expense,1,"Aluguel")).getId();
        var income=categories.saveAndFlush(FinancialCategory.group(owner,FinancialDirection.INCOME,1,"Receitas"));
        incomeCategory=categories.saveAndFlush(FinancialCategory.subcategory(income,1,"Vendas")).getId();
        centerRoot=centers.saveAndFlush(new ResultCenter(owner,"1",null,"Grupo",true)).getId();
        centerLeaf=centers.saveAndFlush(new ResultCenter(owner,"1.1",centerRoot,"Loja Um",true)).getId();
        centerTenth=centers.saveAndFlush(new ResultCenter(owner,"1.10",centerRoot,"Loja Dez",true)).getId();
        centerOther=centers.saveAndFlush(new ResultCenter(owner,"2",null,"Outra Unidade",true)).getId();
        openId=save(owner,"Aluguel 100%","100.00","2026-07-01",null,null,expenseCategory,party,centerLeaf,null,"NF-A","observação Alfa",bank);
        paidId=save(owner,"Venda recebida","200.00","2026-07-02","2026-07-15","210.50",incomeCategory,party,centerTenth,CostBehavior.FIXED,"NF-B","",bank);
        futureId=save(owner,"Receita futura","300.00","2026-07-20",null,null,incomeCategory,null,null,null,"NF-C","",bank);
        save(owner,"Outra unidade","30.00","2026-07-09",null,null,expenseCategory,null,centerOther,CostBehavior.VARIABLE,"NF-D","",bank);
        save(owner,"Pago anterior","40.00","2026-06-01","2026-06-20",null,expenseCategory,party,centerLeaf,null,"NF-E","",bank);
        var foreignBank=banks.saveAndFlush(new BankAccount(other,"Foreign Bank",BankAccountType.CASH,BigDecimal.ZERO,LocalDate.of(2026,1,1),true));
        var foreignGroup=categories.saveAndFlush(FinancialCategory.group(other,FinancialDirection.INCOME,1,"Foreign Group"));
        var foreignCategory=categories.saveAndFlush(FinancialCategory.subcategory(foreignGroup,1,"Foreign Category"));
        foreignId=save(other,"Foreign confidential","999.99","2026-07-05",null,null,foreignCategory.getId(),null,null,null,"FOREIGN","",foreignBank.getId());
    }
    @AfterEach void cleanOwnedSyntheticFixtures() {
        for(var company:List.of(owner,other)) {
            jdbc.update("DELETE FROM financial_entries WHERE company_id=?",company.getId());
            jdbc.update("DELETE FROM financial_bank_accounts WHERE company_id=?",company.getId());jdbc.update("DELETE FROM business_partners WHERE company_id=?",company.getId());
            jdbc.update("DELETE FROM financial_categories WHERE company_id=?",company.getId());jdbc.update("DELETE FROM operational_result_centers WHERE company_id=?",company.getId());
            jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id IN (SELECT id FROM company_access_grants WHERE company_id=?)",company.getId());
            jdbc.update("DELETE FROM company_access_grants WHERE company_id=?",company.getId());jdbc.update("DELETE FROM companies WHERE id=?",company.getId());
        }
        for(var id:clientIds)jdbc.update("DELETE FROM clients WHERE id=?",id);for(var id:firmIds)jdbc.update("DELETE FROM accounting_firms WHERE id=?",id);
        for(var id:userIds)jdbc.update("DELETE FROM user_accounts WHERE id=?",id);
    }
    @Test void grantsMustBeExplicitFullScopeAndCurrentWithNoAdministrativeBypass()throws Exception {
        mvc.perform(get(route()).with(user(principal(viewer))).param("asOfDate","2026-07-10")).andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(5));
        mvc.perform(get(route()).with(user(principal(manager)))).andExpect(status().isOk());
        for(var denied:List.of(admin,restricted))mvc.perform(get(route()).with(user(principal(denied)))).andExpect(status().isForbidden());
        var principal=principal(viewer);
        jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id IN (SELECT id FROM company_access_grants WHERE company_id=? AND user_account_id=?)",owner.getId(),viewer.getId());
        mvc.perform(get(route()).with(user(principal))).andExpect(status().isForbidden());
    }
    @Test void exactAmountsAndFinancialLabelsMustNotExposeFullPartnerProfileOrActorIdentity()throws Exception {
        var result=mvc.perform(get(route()+"/"+paidId).with(user(principal(viewer))).param("asOfDate","2026-07-10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.values.nominalAmount").value("200.00"))
                .andExpect(jsonPath("$.values.actualPaidAmount").value("210.50")).andExpect(jsonPath("$.effectiveAmount").value("210.50"))
                .andExpect(jsonPath("$.values.competence").value("2026-06")).andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.labels.partnerName").value("Fantasia Alfa")).andExpect(jsonPath("$.labels.bankName").value("Banco A"))
                .andExpect(jsonPath("$.effectiveCostBehavior").value("FIXED")).andExpect(jsonPath("$.createdBy").doesNotExist()).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("12ABC34501DE35","private-phone","private@synthetic.invalid","private-street");
    }
    @Test void referencePeriodMustUsePaymentDateInsteadOfDueDateForPaidEntries()throws Exception {
        // PostgreSQL must type each optional date even when only one boundary is supplied.
        mvc.perform(get(route()).with(user(principal(viewer))).param("from","2026-07-01"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(4));
        mvc.perform(get(route()).with(user(principal(viewer))).param("to","2026-07-10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(3));
        mvc.perform(get(route()).with(user(principal(viewer))).param("from","2026-07-01").param("to","2026-07-10").param("asOfDate","2026-07-10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(2));
        mvc.perform(get(route()).with(user(principal(viewer))).param("status","PAID").param("from","2026-07-01").param("to","2026-07-10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(0));
        mvc.perform(get(route()).with(user(principal(viewer))).param("status","PAID").param("from","2026-07-15").param("to","2026-07-15"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(1)).andExpect(jsonPath("$.items[0].id").value(paidId.toString()));
    }
    @Test void openOverdueAndDueTodayMustFollowTheExplicitAsOfDate()throws Exception {
        mvc.perform(get(route()).with(user(principal(viewer))).param("status","OPEN").param("asOfDate","2026-07-10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(3));
        mvc.perform(get(route()).with(user(principal(viewer))).param("status","OVERDUE").param("asOfDate","2026-07-10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(2));
        mvc.perform(get(route()+"/"+futureId).with(user(principal(viewer))).param("asOfDate","2026-07-20"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("OPEN"));
    }
    @Test void categoryGroupsCenterSubtreesAndEffectiveCostMustBeFilteredInsideTheCompany()throws Exception {
        mvc.perform(get(route()).with(user(principal(viewer))).param("categoryId",expenseGroup.toString())).andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(3));
        mvc.perform(get(route()).with(user(principal(viewer))).param("centerId",centerRoot.toString())).andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(3));
        mvc.perform(get(route()).with(user(principal(viewer))).param("centerId",centerLeaf.toString())).andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(2));
        mvc.perform(get(route()).with(user(principal(viewer))).param("withoutCenter","true")).andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(1));
        mvc.perform(get(route()).with(user(principal(viewer))).param("costBehavior","VARIABLE")).andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(2));
        mvc.perform(get(route()).with(user(principal(viewer))).param("costBehavior","FIXED")).andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(3));
    }
    @Test void searchMustBeLiteralCaseInsensitiveAndKeepAccentsAsInTheReference()throws Exception {
        mvc.perform(get(route()).with(user(principal(viewer))).param("search","%")).andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(1));
        mvc.perform(get(route()).with(user(principal(viewer))).param("search","12abc34501de35")).andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(3));
        mvc.perform(get(route()).with(user(principal(viewer))).param("search","OBSERVAÇÃO")).andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(1));
        mvc.perform(get(route()).with(user(principal(viewer))).param("search","observacao")).andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(0));
        mvc.perform(get(route()).with(user(principal(viewer))).param("search","Foreign confidential")).andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(0));
    }
    @Test void paginationAndForeignLookupsMustNotExposeDataOrEnableWrites()throws Exception {
        mvc.perform(get(route()).with(user(principal(viewer))).param("page","0").param("size","2")).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(2)).andExpect(jsonPath("$.totalItems").value(5));
        mvc.perform(get(route()+"/"+foreignId).with(user(principal(viewer)))).andExpect(status().isNotFound());
        mvc.perform(get("/api/companies/"+other.getId()+"/financial-entries").with(user(principal(viewer)))).andExpect(status().isForbidden());
        mvc.perform(get(route())).andExpect(status().isUnauthorized());
        mvc.perform(post(route()).with(user(principal(manager))).with(csrf()).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(put(route()+"/"+openId).with(user(principal(manager))).with(csrf()).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(delete(route()+"/"+openId).with(user(principal(manager))).with(csrf())).andExpect(status().isForbidden());
    }
    @Test void invalidRangesSubtreesOrBindingsMustFailWithoutBroadeningTheQuery()throws Exception {
        mvc.perform(get(route()).with(user(principal(viewer))).param("size","101")).andExpect(status().isBadRequest());
        mvc.perform(get(route()).with(user(principal(viewer))).param("from","2026-08-01").param("to","2026-07-01")).andExpect(status().isBadRequest());
        mvc.perform(get(route()).with(user(principal(viewer))).param("centerId",UUID.randomUUID().toString())).andExpect(status().isNotFound());
        mvc.perform(get(route()).with(user(principal(viewer))).param("centerId",centerRoot.toString()).param("withoutCenter","true")).andExpect(status().isBadRequest());
        mvc.perform(get(route()).with(user(principal(viewer))).param("status","UNSUPPORTED")).andExpect(status().isBadRequest());
    }
    @Test void valueProjectionMustIgnoreAnAlreadyManagedStaleEntry()throws Exception {
        var loaded=new CountDownLatch(1);var proceed=new CountDownLatch(1);var pool=Executors.newSingleThreadExecutor();
        try {
            var reading=pool.submit(()->new TransactionTemplate(transactions).execute(status->{
                assertThat(entries.findById(openId).orElseThrow().getValues().nominalAmount()).isEqualByComparingTo("100.00");loaded.countDown();
                try {if(!proceed.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Synthetic read barrier");}
                catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException("Synthetic interrupted barrier");}
                return service.find(viewer.getId(),owner.getId(),openId,LocalDate.of(2026,7,10));
            }));
            assertThat(loaded.await(5,TimeUnit.SECONDS)).isTrue();
            var row=entries.findById(openId).orElseThrow();var v=row.getValues();
            row.replace(new FinancialEntryValues(v.direction(),v.description(),new BigDecimal("123.45"),v.documentDate(),v.entryDate(),v.competence(),v.dueDate(),v.paymentDate(),v.actualPaidAmount(),
                    v.bankAccountId(),v.categoryId(),v.partnerId(),v.resultCenterId(),v.costBehaviorOverride(),v.paymentMethod(),v.documentNumber(),v.notes()),viewer.getId());entries.saveAndFlush(row);
            proceed.countDown();assertThat(reading.get(10,TimeUnit.SECONDS).values().nominalAmount()).isEqualByComparingTo("123.45");
        }finally {proceed.countDown();pool.shutdownNow();}
    }
    private UUID save(Company company,String description,String nominal,String due,String payment,String actual,UUID category,UUID partner,UUID center,CostBehavior cost,String doc,String notes,UUID bank) {
        return entries.saveAndFlush(new FinancialEntry(company,new FinancialEntryValues(
                categories.findById(category).orElseThrow().getDirection(),description,new BigDecimal(nominal),LocalDate.of(2026,6,30),LocalDate.of(2026,7,1),YearMonth.of(2026,6),
                LocalDate.parse(due),payment==null?null:LocalDate.parse(payment),actual==null?null:new BigDecimal(actual),bank,category,partner,center,cost,"",doc,notes),viewer.getId())).getId();
    }
    private Company company(String suffix,String cnpj) {
        var firm=firms.saveAndFlush(new AccountingFirm("Synthetic Read Firm "+suffix,true));firmIds.add(firm.getId());
        var client=clients.saveAndFlush(new Client(firm,"Synthetic Read Client "+suffix,true));clientIds.add(client.getId());
        return companies.saveAndFlush(new Company(client,"Synthetic Read Company "+suffix,cnpj));
    }
    private UserAccount createUser(String name) {var row=users.saveAndFlush(new UserAccount("Synthetic Read "+name,UUID.randomUUID()+"@example.invalid","hash"));userIds.add(row.getId());return row;}
    private void grant(UserAccount actor,CompanyAccessRole role,CostCenterScope scope,Set<CompanyPermission> permissions) {grants.saveAndFlush(new CompanyAccessGrant(actor,owner,role,scope,permissions));}
    private UserAccountPrincipal principal(UserAccount actor) {return new UserAccountPrincipal(actor);}
    private String route() {return "/api/companies/"+owner.getId()+"/financial-entries";}
}
