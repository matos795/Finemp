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

@SpringBootTest(properties={"finemp.finance.catalog.enabled=true","finemp.finance.entries.enabled=true"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class FinancialEntryPersistenceIntegrationTest {
    @Autowired FinancialEntryRepository entries;
    @Autowired CompanyRepository companies;
    @Autowired ClientRepository clients;
    @Autowired AccountingFirmRepository firms;
    @Autowired UserAccountRepository users;
    @Autowired CompanyAccessGrantRepository grants;
    @Autowired BankAccountRepository banks;
    @Autowired BusinessPartnerRepository partners;
    @Autowired FinancialCategoryRepository categories;
    @Autowired ResultCenterRepository centers;
    @Autowired BankAccountService bankService;
    @Autowired BusinessPartnerService partnerService;
    @Autowired FinancialCategoryService categoryService;
    @Autowired ResultCenterService centerService;
    @Autowired @org.springframework.beans.factory.annotation.Qualifier("financialEntryBankReferences") BankAccountReferenceGuard bankUsage;
    @Autowired BusinessPartnerReferenceGuard partnerUsage;
    @Autowired FinancialCategoryUsageGuard categoryUsage;
    @Autowired ResultCenterUsageGuard centerUsage;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired PlatformTransactionManager transactions;
    private Company owner,other;
    private UserAccount actor;
    private UUID bank,partner,group,category,center,foreignBank,foreignPartner,foreignCategory,foreignCenter,entryId;
    private final List<UUID> firmIds=new ArrayList<>(),clientIds=new ArrayList<>();
    @BeforeEach void prepare() {
        owner=company("Owner","71717171000171");other=company("Other","72727272000172");
        actor=users.saveAndFlush(new UserAccount("Synthetic Entry Manager",UUID.randomUUID()+"@example.invalid","hash"));
        grants.saveAndFlush(new CompanyAccessGrant(actor,owner,CompanyAccessRole.CUSTOM,CostCenterScope.ALL,
                Set.of(CompanyPermission.BANK_ACCOUNT_MANAGE,CompanyPermission.BUSINESS_PARTNER_MANAGE,CompanyPermission.FINANCIAL_CATEGORY_MANAGE,CompanyPermission.RESULT_CENTER_MANAGE)));
        bank=banks.saveAndFlush(new BankAccount(owner,"Synthetic Entry Bank",BankAccountType.CASH,BigDecimal.ZERO,LocalDate.of(2026,1,1),true)).getId();
        foreignBank=banks.saveAndFlush(new BankAccount(other,"Foreign Entry Bank",BankAccountType.CASH,BigDecimal.ZERO,LocalDate.of(2026,1,1),true)).getId();
        partner=partners.saveAndFlush(new BusinessPartner(owner,profile())).getId();foreignPartner=partners.saveAndFlush(new BusinessPartner(other,profile())).getId();
        var root=categories.saveAndFlush(FinancialCategory.group(owner,FinancialDirection.INCOME,1,"Synthetic Entry Group"));group=root.getId();
        category=categories.saveAndFlush(FinancialCategory.subcategory(root,1,"Synthetic Entry Category")).getId();
        var foreignRoot=categories.saveAndFlush(FinancialCategory.group(other,FinancialDirection.INCOME,1,"Foreign Entry Group"));
        foreignCategory=categories.saveAndFlush(FinancialCategory.subcategory(foreignRoot,1,"Foreign Entry Category")).getId();
        center=centers.saveAndFlush(new ResultCenter(owner,"1",null,"Synthetic Entry Center",true)).getId();
        foreignCenter=centers.saveAndFlush(new ResultCenter(other,"1",null,"Foreign Entry Center",true)).getId();
        entryId=entries.saveAndFlush(new FinancialEntry(owner,values(bank,category,partner,center,new BigDecimal("1.2300"),null,null),actor.getId())).getId();
    }
    @AfterEach void cleanOwnedSyntheticFixtures() {
        for(var company:List.of(owner,other)) {
            jdbc.update("DELETE FROM financial_entries WHERE company_id=?",company.getId());
            jdbc.update("DELETE FROM financial_bank_accounts WHERE company_id=?",company.getId());
            jdbc.update("DELETE FROM business_partners WHERE company_id=?",company.getId());
            jdbc.update("DELETE FROM financial_categories WHERE company_id=?",company.getId());
            jdbc.update("DELETE FROM operational_result_centers WHERE company_id=?",company.getId());
            jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id IN (SELECT id FROM company_access_grants WHERE company_id=?)",company.getId());
            jdbc.update("DELETE FROM company_access_grants WHERE company_id=?",company.getId());jdbc.update("DELETE FROM companies WHERE id=?",company.getId());
        }
        for(var id:clientIds)jdbc.update("DELETE FROM clients WHERE id=?",id);
        for(var id:firmIds)jdbc.update("DELETE FROM accounting_firms WHERE id=?",id);
        jdbc.update("DELETE FROM user_accounts WHERE id=?",actor.getId());
    }
    @Test void exactStateAndTraceabilityMustRoundTripWithoutChangingIndependentDates() {
        var row=entries.findByIdAndCompanyId(entryId,owner.getId()).orElseThrow();
        assertThat(row.getValues().nominalAmount()).isEqualTo(new BigDecimal("1.23"));
        assertThat(row.getValues().competence()).isEqualTo(YearMonth.of(2025,12));
        assertThat(row.getValues().entryDate()).isAfter(row.getValues().dueDate());
        assertThat(row.getCreatedBy()).isEqualTo(actor.getId());assertThat(row.getUpdatedBy()).isEqualTo(actor.getId());
        assertThat(row.getCreatedAt()).isNotNull();assertThat(row.getUpdatedAt()).isNotNull();assertThat(row.getVersion()).isZero();
        row.replace(values(bank,category,partner,center,BigDecimal.ZERO,LocalDate.of(2026,2,1),new BigDecimal("0.90")),actor.getId());
        var changed=entries.saveAndFlush(row);
        assertThat(changed.getVersion()).isEqualTo(1);assertThat(changed.getValues().effectiveAmount()).isEqualByComparingTo("0.90");
        assertThat(changed.getCreatedAt()).isEqualTo(row.getCreatedAt());
    }
    @Test void lookupsAndReferenceProbesMustNotLeakAcrossCompaniesClientsOrFirms() {
        assertThat(entries.findByIdAndCompanyId(entryId,other.getId())).isEmpty();
        assertThat(bankUsage.hasReferences(owner.getId(),bank)).isTrue();assertThat(bankUsage.hasReferences(other.getId(),bank)).isFalse();
        assertThat(partnerUsage.hasReferences(owner.getId(),partner)).isTrue();assertThat(partnerUsage.hasReferences(other.getId(),partner)).isFalse();
        assertThat(categoryUsage.hasEntriesInCategoryOrChildren(owner.getId(),group)).isTrue();
        assertThat(categoryUsage.hasEntriesInCategoryOrChildren(other.getId(),group)).isFalse();
        assertThat(centerUsage.hasEntries(owner.getId(),center)).isTrue();assertThat(centerUsage.hasReferences(other.getId(),center)).isFalse();
    }
    @Test void compositeForeignKeysMustRejectEachCrossCompanyReference() {
        for(var references:List.of(
                new UUID[]{foreignBank,category,partner,center},new UUID[]{bank,foreignCategory,partner,center},
                new UUID[]{bank,category,foreignPartner,center},new UUID[]{bank,category,partner,foreignCenter})) {
            new TransactionTemplate(transactions).executeWithoutResult(status->{
                assertThatThrownBy(()->entries.saveAndFlush(new FinancialEntry(owner,values(references[0],references[1],references[2],references[3],BigDecimal.ONE,null,null),actor.getId())))
                        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
                status.setRollbackOnly();
            });
        }
    }
    @Test void catalogServicesMustPreserveActualEntryHistoryAndRefuseUsedCategories() {
        assertThat(bankService.delete(actor.getId(),owner.getId(),bank,0).action()).isEqualTo(BankAccountService.DeleteAction.DEACTIVATED);
        assertThat(partnerService.delete(actor.getId(),owner.getId(),partner,0).action()).isEqualTo(BusinessPartnerService.DeleteAction.DEACTIVATED);
        assertThat(centerService.delete(actor.getId(),owner.getId(),center,0).action()).isEqualTo(ResultCenterService.DeleteAction.DEACTIVATED);
        for(var id:List.of(category,group))assertThatThrownBy(()->categoryService.delete(actor.getId(),owner.getId(),id,0))
                .isInstanceOfSatisfying(FinancialCategoryConflictException.class,e->assertThat(e.reason()).isEqualTo(FinancialCategoryConflictException.Reason.CATEGORY_IN_USE));
        assertThat(entries.findById(entryId)).isPresent();
        assertThat(banks.findById(bank).orElseThrow().isActive()).isFalse();assertThat(partners.findById(partner).orElseThrow().getProfile().active()).isFalse();
    }
    @Test void centerWithAnActualEntryMustNotBecomeAGroup() {
        assertThatThrownBy(()->centerService.update(actor.getId(),owner.getId(),center,new ResultCenterUpdateRequest("Cannot Become Group",true,0L,ResultCenterType.GROUP)))
                .isInstanceOfSatisfying(ResultCenterConflictException.class,e->assertThat(e.reason()).isEqualTo(ResultCenterConflictException.Reason.HAS_ENTRIES));
    }
    @Test void equalUuidsInDifferentCatalogsMustNotConfuseTheReferenceAdapters() {
        jdbc.update("INSERT INTO financial_bank_accounts(id,company_id,name,type,opening_balance,opening_date,active,version) VALUES (?,?,'Synthetic Shared UUID','CASH',0,DATE '2026-01-01',TRUE,0)",partner,owner.getId());
        assertThat(partnerUsage.hasReferences(owner.getId(),partner)).isTrue();assertThat(bankUsage.hasReferences(owner.getId(),partner)).isFalse();
        assertThat(bankService.delete(actor.getId(),owner.getId(),partner,0).action()).isEqualTo(BankAccountService.DeleteAction.DELETED);
        assertThat(partners.findById(partner)).isPresent();
    }
    @Test void staleDetachedWriteMustNotOverwriteAMoreRecentFinancialState() {
        var first=entries.findById(entryId).orElseThrow();var stale=entries.findById(entryId).orElseThrow();
        first.replace(values(bank,category,partner,center,new BigDecimal("2.00"),null,null),actor.getId());entries.saveAndFlush(first);
        stale.replace(values(bank,category,partner,center,new BigDecimal("3.00"),null,null),actor.getId());
        assertThatThrownBy(()->entries.saveAndFlush(stale)).isInstanceOf(org.springframework.orm.ObjectOptimisticLockingFailureException.class);
        assertThat(entries.findById(entryId).orElseThrow().getValues().nominalAmount()).isEqualByComparingTo("2.00");
    }
    @Test void thisPersistenceFoundationMustNotExposeAnUnfinishedFinancialEntryWriter()throws Exception {
        String route="/api/companies/"+owner.getId()+"/financial-entries";
        mvc.perform(get(route).with(user(new UserAccountPrincipal(actor)))).andExpect(status().isForbidden());
        mvc.perform(post(route).with(user(new UserAccountPrincipal(actor))).with(csrf()).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        assertThat(entries.findById(entryId)).isPresent();
    }
    private FinancialEntryValues values(UUID bank,UUID category,UUID partner,UUID center,BigDecimal nominal,LocalDate paid,BigDecimal actual) {
        return new FinancialEntryValues(FinancialDirection.INCOME,"Synthetic Canonical Entry",nominal,LocalDate.of(2026,1,31),LocalDate.of(2026,2,5),YearMonth.of(2025,12),
                LocalDate.of(2026,1,15),paid,actual,bank,category,partner,center,null,"","","");
    }
    private Company company(String suffix,String cnpj) {
        var firm=firms.saveAndFlush(new AccountingFirm("Synthetic Entry Firm "+suffix,true));firmIds.add(firm.getId());
        var client=clients.saveAndFlush(new Client(firm,"Synthetic Entry Client "+suffix,true));clientIds.add(client.getId());
        return companies.saveAndFlush(new Company(client,"Synthetic Entry Company "+suffix,cnpj));
    }
    private BusinessPartnerProfile profile() {
        return new BusinessPartnerProfile(PartnerPersonType.LEGAL_ENTITY,"12ABC34501DE35","Synthetic Entry Partner","",PartnerRole.BOTH,
                "","","","","","","","","","","","","","",true);
    }
}
