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
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={"finemp.finance.entries.enabled=true","finemp.finance.catalog.enabled=true"})
@Import(TestcontainersConfiguration.class)
class FinancialEntryCommandIntegrationTest {
    @Autowired FinancialEntryCommandService service;
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
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    Company owner,other;UserAccount manager,viewer;
    UUID bank,foreignBank,category,group,foreignCategory,party,foreignParty,center,groupCenter,foreignCenter;
    final List<UUID> firmIds=new ArrayList<>(),clientIds=new ArrayList<>(),userIds=new ArrayList<>();
    @BeforeEach void prepare() {
        owner=company("Owner","75757575000175");other=company("Other","76767676000176");
        manager=user("Manager");viewer=user("Viewer");
        grant(manager,Set.of(CompanyPermission.FINANCIAL_ENTRY_MANAGE));grant(viewer,Set.of(CompanyPermission.FINANCIAL_ENTRY_VIEW));
        bank=bank(owner);foreignBank=bank(other);
        var root=categories.saveAndFlush(FinancialCategory.group(owner,FinancialDirection.EXPENSE,1,"Synthetic Expenses"));group=root.getId();
        category=categories.saveAndFlush(FinancialCategory.subcategory(root,1,"Synthetic Category")).getId();
        var foreignRoot=categories.saveAndFlush(FinancialCategory.group(other,FinancialDirection.EXPENSE,1,"Foreign Group"));
        foreignCategory=categories.saveAndFlush(FinancialCategory.subcategory(foreignRoot,1,"Foreign Category")).getId();
        party=party(owner);foreignParty=party(other);
        groupCenter=centers.saveAndFlush(new ResultCenter(owner,"1",null,"Synthetic Group",true)).getId();
        center=centers.saveAndFlush(new ResultCenter(owner,"1.1",groupCenter,"Synthetic Leaf",true)).getId();
        foreignCenter=centers.saveAndFlush(new ResultCenter(other,"1",null,"Foreign Leaf",true)).getId();
    }
    @AfterEach void cleanSyntheticFixturesOnly() {
        for(var company:List.of(owner,other)) {
            for(var table:List.of("financial_entry_events","financial_entry_command_receipts","financial_entry_command_keys","financial_entries","financial_company_settings",
                    "financial_bank_accounts","business_partners","financial_categories","operational_result_centers"))
                jdbc.update("DELETE FROM "+table+" WHERE company_id=?",company.getId());
            jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id IN (SELECT id FROM company_access_grants WHERE company_id=?)",company.getId());
            jdbc.update("DELETE FROM company_access_grants WHERE company_id=?",company.getId());jdbc.update("DELETE FROM companies WHERE id=?",company.getId());
        }
        for(var id:clientIds)jdbc.update("DELETE FROM clients WHERE id=?",id);for(var id:firmIds)jdbc.update("DELETE FROM accounting_firms WHERE id=?",id);
        for(var id:userIds)jdbc.update("DELETE FROM user_accounts WHERE id=?",id);
    }
    @Test void createAndUpdateMustCommitExactStateVersionReceiptAndBeforeAfterAuditTogether() {
        var created=create(UUID.randomUUID(),data("100.01","Original",null,bank,category,party,center));
        assertThat(created.action()).isEqualTo(EntryCommandAction.CREATED);assertThat(created.version()).isZero();
        var changed=service.update(manager.getId(),owner.getId(),UUID.randomUUID(),created.entryId(),0L,data("123.45","Changed",null,bank,category,party,center));
        assertThat(changed.version()).isEqualTo(1);assertThat(entries.findById(created.entryId()).orElseThrow().getValues().nominalAmount()).isEqualByComparingTo("123.45");
        assertThat(count("financial_entry_events")).isEqualTo(2);assertThat(count("financial_entry_command_receipts")).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT before_state::jsonb->>'nominalAmount' FROM financial_entry_events WHERE company_id=? AND action='UPDATED'",String.class,owner.getId())).isEqualTo("100.01");
        assertThat(jdbc.queryForObject("SELECT after_state::jsonb->>'nominalAmount' FROM financial_entry_events WHERE company_id=? AND action='UPDATED'",String.class,owner.getId())).isEqualTo("123.45");
    }
    @Test void replayMustReturnOriginalReceiptEvenAfterLaterChangesAndStillRequireCurrentPermission() {
        var key=UUID.randomUUID();var data=data();var first=create(key,data);
        service.update(manager.getId(),owner.getId(),UUID.randomUUID(),first.entryId(),0L,data("2.00","Changed",null,bank,category,null,null));
        assertThat(create(key,data)).isEqualTo(first);assertThat(count("financial_entries")).isEqualTo(1);assertThat(count("financial_entry_events")).isEqualTo(2);
        assertThatThrownBy(()->create(key,data("3.00","Different",null,bank,category,null,null))).isInstanceOf(FinancialEntryCommandConflictException.class);
        revoke(manager);assertThatThrownBy(()->create(key,data)).isInstanceOf(com.finemp.administration.OrganizationAccessDeniedException.class);
    }
    @Test void bankCategoryPartnerAndCenterReferencesMustNeverCrossCompanyBoundaries() {
        for(var invalid:List.of(data("1","Foreign bank",null,foreignBank,category,null,null),data("1","Foreign category",null,bank,foreignCategory,null,null),
                data("1","Foreign party",null,bank,category,foreignParty,null),data("1","Foreign center",null,bank,category,null,foreignCenter)))
            assertThatThrownBy(()->create(UUID.randomUUID(),invalid)).isInstanceOf(IllegalArgumentException.class);
        assertThat(count("financial_entries")).isZero();assertThat(count("financial_entry_events")).isZero();
    }
    @Test void categoryMustBeAnalyticAndDirectionCompatibleAndPartnerRoleMustBeCurrentWithoutPromotion() {
        assertThatThrownBy(()->create(UUID.randomUUID(),data("1","Group forbidden",null,bank,group,null,null))).isInstanceOf(IllegalArgumentException.class);
        var income=categories.saveAndFlush(FinancialCategory.group(owner,FinancialDirection.INCOME,1,"Income"));
        var incomeSub=categories.saveAndFlush(FinancialCategory.subcategory(income,1,"Sales"));
        assertThatThrownBy(()->create(UUID.randomUUID(),data("1","Wrong direction",null,bank,incomeSub.getId(),null,null))).isInstanceOf(IllegalArgumentException.class);
        jdbc.update("UPDATE business_partners SET role='CUSTOMER' WHERE id=?",party);
        assertThatThrownBy(()->create(UUID.randomUUID(),data("1","Wrong role",null,bank,category,party,null))).isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("SELECT role FROM business_partners WHERE id=?",String.class,party)).isEqualTo("CUSTOMER");
    }
    @Test void centerRequirementAppliesOnlyToCreateAndHistoricalInactiveCenterCanRemainSelected() {
        var noCenter=create(UUID.randomUUID(),data());var withCenter=create(UUID.randomUUID(),data("1","Has center",null,bank,category,null,center));
        jdbc.update("INSERT INTO financial_company_settings(company_id,require_result_center) VALUES(?,true)",owner.getId());
        assertThatThrownBy(()->create(UUID.randomUUID(),data())).isInstanceOf(IllegalArgumentException.class);
        service.update(manager.getId(),owner.getId(),UUID.randomUUID(),noCenter.entryId(),0L,data("2","No retroactive center",null,bank,category,null,null));
        jdbc.update("UPDATE operational_result_centers SET active=false WHERE id=?",center);
        service.update(manager.getId(),owner.getId(),UUID.randomUUID(),withCenter.entryId(),0L,data("2","Keep historical center",null,bank,category,null,center));
        assertThatThrownBy(()->create(UUID.randomUUID(),data("1","Inactive center",null,bank,category,null,center))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->create(UUID.randomUUID(),data("1","Group center",null,bank,category,null,groupCenter))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void inactiveBankAndPartnerMustNotAcquireAnInventedUniversalVeto() {
        jdbc.update("UPDATE financial_bank_accounts SET active=false WHERE id=?",bank);jdbc.update("UPDATE business_partners SET active=false WHERE id=?",party);
        assertThat(create(UUID.randomUUID(),data("1","Existing inactive references",null,bank,category,party,null)).entryId()).isNotNull();
    }
    @Test void paidEditPreservesTheActualSettlementAndUnpaidEditClearsIt() {
        var paidDate=LocalDate.of(2026,7,2);var receipt=create(UUID.randomUUID(),data("100","Paid",paidDate,bank,category,null,null));
        jdbc.update("UPDATE financial_entries SET actual_paid_amount=110.50 WHERE id=?",receipt.entryId());
        service.update(manager.getId(),owner.getId(),UUID.randomUUID(),receipt.entryId(),0L,data("120","Still paid",paidDate,bank,category,null,null));
        assertThat(entries.findById(receipt.entryId()).orElseThrow().getValues().effectiveAmount()).isEqualByComparingTo("110.50");
        service.update(manager.getId(),owner.getId(),UUID.randomUUID(),receipt.entryId(),1L,data("120","Now unpaid",null,bank,category,null,null));
        var values=entries.findById(receipt.entryId()).orElseThrow().getValues();assertThat(values.actualPaidAmount()).isNull();assertThat(values.effectiveAmount()).isEqualByComparingTo("120.00");
    }
    @Test void receiptInsertFailureMustRollbackTheEntryAndAuditEventAndTransactionalDdl() {
        var key=UUID.randomUUID();
        assertThatThrownBy(()->new TransactionTemplate(transactions).execute(status->{
            jdbc.execute("ALTER TABLE financial_entry_command_receipts ADD CONSTRAINT synthetic_receipt_failure CHECK(command_id <> '"+key+"'::uuid)");
            return create(key,data());
        })).isInstanceOf(DataIntegrityViolationException.class);
        for(var table:List.of("financial_entries","financial_entry_events","financial_entry_command_receipts"))assertThat(count(table)).isZero();
        assertThat(create(key,data()).entryId()).isNotNull();
    }
    @Test void concurrentSameCommandMustPublishOneEntryOneEventAndOneReceipt()throws Exception {
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);var key=UUID.randomUUID();var data=data();
        try {
            Callable<FinancialEntryCommandReceipt> task=()->{if(!start.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Synthetic barrier");return create(key,data);};
            var first=pool.submit(task);var second=pool.submit(task);start.countDown();assertThat(first.get(15,TimeUnit.SECONDS)).isEqualTo(second.get(15,TimeUnit.SECONDS));
            for(var table:List.of("financial_entries","financial_entry_events","financial_entry_command_receipts"))assertThat(count(table)).isEqualTo(1);
        }finally {start.countDown();pool.shutdownNow();}
    }
    @Test void concurrentUpdatesWithTheSameExpectedVersionMustAllowOnlyOneStateTransition()throws Exception {
        var original=create(UUID.randomUUID(),data());var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            Callable<Boolean> task=()->{if(!start.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Synthetic barrier");
                try {service.update(manager.getId(),owner.getId(),UUID.randomUUID(),original.entryId(),0L,data("2","Updated",null,bank,category,null,null));return true;}
                catch(FinancialEntryCommandConflictException conflict) {assertThat(conflict.getReason()).isEqualTo(FinancialEntryCommandConflictException.Reason.STALE_VERSION);return false;}};
            var first=pool.submit(task);var second=pool.submit(task);start.countDown();assertThat(List.of(first.get(15,TimeUnit.SECONDS),second.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
            assertThat(count("financial_entry_events")).isEqualTo(2);assertThat(count("financial_entry_command_receipts")).isEqualTo(2);
        }finally {start.countDown();pool.shutdownNow();}
    }
    @Test void scalarReferenceValidationMustIgnoreAnAlreadyManagedStaleCenter()throws Exception {
        var pool=Executors.newSingleThreadExecutor();
        try {
            assertThatThrownBy(()->new TransactionTemplate(transactions).execute(status->{
                assertThat(centers.findById(center).orElseThrow().isActive()).isTrue();
                try {pool.submit(()->jdbc.update("UPDATE operational_result_centers SET active=false WHERE id=?",center)).get(5,TimeUnit.SECONDS);}
                catch(Exception failure) {throw new IllegalStateException("Synthetic reference update failed",failure);}
                return create(UUID.randomUUID(),data("1","Stale active center",null,bank,category,null,center));
            })).isInstanceOf(IllegalArgumentException.class);
            assertThat(count("financial_entries")).isZero();
        }finally {pool.shutdownNow();}
    }
    @Test void viewOnlyForeignTargetAndUnsupportedAmbientIsolationMustFailClosed() {
        assertThatThrownBy(()->service.create(viewer.getId(),owner.getId(),UUID.randomUUID(),data())).isInstanceOf(com.finemp.administration.OrganizationAccessDeniedException.class);
        assertThatThrownBy(()->service.create(manager.getId(),other.getId(),UUID.randomUUID(),data())).isInstanceOf(com.finemp.administration.OrganizationAccessDeniedException.class);
        assertThatThrownBy(()->service.update(manager.getId(),owner.getId(),UUID.randomUUID(),UUID.randomUUID(),0L,data())).isInstanceOf(com.finemp.administration.SaasResourceNotFoundException.class);
        var repeatable=new TransactionTemplate(transactions);repeatable.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        assertThatThrownBy(()->repeatable.execute(status->create(UUID.randomUUID(),data()))).isInstanceOf(FinancialEntryCommandConflictException.class);
        assertThat(count("financial_entries")).isZero();
    }
    @Test void staleManagedEntryMustConflictEvenWhenTheCallerSuppliesTheCurrentDatabaseVersion()throws Exception {
        var created=create(UUID.randomUUID(),data());var pool=Executors.newSingleThreadExecutor();
        try {
            assertThatThrownBy(()->new TransactionTemplate(transactions).execute(status->{
                assertThat(entries.findById(created.entryId()).orElseThrow().getVersion()).isZero();
                try {pool.submit(()->service.update(manager.getId(),owner.getId(),UUID.randomUUID(),created.entryId(),0L,
                        data("2","External committed state",null,bank,category,null,null))).get(10,TimeUnit.SECONDS);}
                catch(Exception failure) {throw new IllegalStateException("Synthetic concurrent update failed",failure);}
                return service.update(manager.getId(),owner.getId(),UUID.randomUUID(),created.entryId(),1L,data("3","Stale context update",null,bank,category,null,null));
            })).isInstanceOf(FinancialEntryCommandConflictException.class);
            assertThat(count("financial_entry_events")).isEqualTo(2);
            assertThat(entries.findById(created.entryId()).orElseThrow().getValues().nominalAmount()).isEqualByComparingTo("2.00");
        }finally {pool.shutdownNow();}
    }
    FinancialEntryCommandReceipt create(UUID key,FinancialEntryCommandData data) {return service.create(manager.getId(),owner.getId(),key,data);}
    FinancialEntryCommandData data() {return data("1","Synthetic description",null,bank,category,null,null);}
    FinancialEntryCommandData data(String amount,String description,LocalDate paid,UUID bankId,UUID categoryId,UUID partnerId,UUID centerId) {
        var date=LocalDate.of(2026,7,1);return new FinancialEntryCommandData(FinancialDirection.EXPENSE,description,new BigDecimal(amount),date,date,YearMonth.of(2026,7),date,paid,
                bankId,categoryId,partnerId,centerId,null,"","SYNTHETIC","");
    }
    long count(String table) {return jdbc.queryForObject("SELECT count(*) FROM "+table+" WHERE company_id=?",Long.class,owner.getId());}
    UUID bank(Company company) {return banks.saveAndFlush(new BankAccount(company,"Synthetic Bank",BankAccountType.CASH,BigDecimal.ZERO,LocalDate.of(2026,1,1),true)).getId();}
    UUID party(Company company) {return partners.saveAndFlush(new BusinessPartner(company,new BusinessPartnerProfile(PartnerPersonType.LEGAL_ENTITY,"12ABC34501DE35","Synthetic Party","",PartnerRole.BOTH,
            "","","","","","","","","","","","","","",true))).getId();}
    Company company(String name,String document) {
        var firm=firms.saveAndFlush(new AccountingFirm("Synthetic Command Firm "+name,true));firmIds.add(firm.getId());
        var client=clients.saveAndFlush(new Client(firm,"Synthetic Command Client "+name,true));clientIds.add(client.getId());
        return companies.saveAndFlush(new Company(client,"Synthetic Command Company "+name,document));
    }
    UserAccount user(String name) {var actor=users.saveAndFlush(new UserAccount("Synthetic Command "+name,UUID.randomUUID()+"@example.invalid","hash"));userIds.add(actor.getId());return actor;}
    void grant(UserAccount actor,Set<CompanyPermission> permissions) {grants.saveAndFlush(new CompanyAccessGrant(actor,owner,CompanyAccessRole.CUSTOM,CostCenterScope.ALL,permissions));}
    void revoke(UserAccount actor) {jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id IN (SELECT id FROM company_access_grants WHERE company_id=? AND user_account_id=?)",owner.getId(),actor.getId());}
}
