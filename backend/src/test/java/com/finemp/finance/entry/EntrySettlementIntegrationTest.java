package com.finemp.finance.entry;

import com.finemp.TestcontainersConfiguration;
import com.finemp.access.*;
import com.finemp.accountingfirm.*;
import com.finemp.client.*;
import com.finemp.company.*;
import com.finemp.finance.bank.*;
import com.finemp.finance.category.*;
import com.finemp.finance.center.*;
import com.finemp.finance.partner.*;
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
class EntrySettlementIntegrationTest {
    @Autowired EntrySettlementCommandService service;@Autowired FinancialEntryCommandService creator;@Autowired FinancialEntryRepository entries;
    @Autowired CompanyRepository companies;@Autowired ClientRepository clients;@Autowired AccountingFirmRepository firms;
    @Autowired UserAccountRepository users;@Autowired CompanyAccessGrantRepository grants;
    @Autowired BankAccountRepository banks;@Autowired FinancialCategoryRepository categories;@Autowired BusinessPartnerRepository partners;@Autowired ResultCenterRepository centers;
    @Autowired JdbcTemplate jdbc;@Autowired PlatformTransactionManager transactions;
    Company owner,other;UserAccount manager,otherManager,viewer,restricted;
    UUID bankA,bankB,foreignBank,category,foreignCategory,party,center,entry,foreignEntry,createKey;
    final LocalDate date=LocalDate.of(2026,7,1),paid=LocalDate.of(2026,7,10);
    final List<UUID> firmIds=new ArrayList<>(),clientIds=new ArrayList<>(),userIds=new ArrayList<>();
    @BeforeEach void prepare() {
        owner=company("Owner","79797979000179");other=company("Other","80808080000180");
        manager=user("Manager");otherManager=user("Other manager");viewer=user("Viewer");restricted=user("Restricted");
        grant(manager,owner,CostCenterScope.ALL,CompanyPermission.FINANCIAL_ENTRY_MANAGE);grant(otherManager,other,CostCenterScope.ALL,CompanyPermission.FINANCIAL_ENTRY_MANAGE);
        grant(viewer,owner,CostCenterScope.ALL,CompanyPermission.FINANCIAL_ENTRY_VIEW);grant(restricted,owner,CostCenterScope.RESTRICTED,CompanyPermission.FINANCIAL_ENTRY_MANAGE);
        bankA=bank(owner,"Original");bankB=bank(owner,"Settlement");foreignBank=bank(other,"Foreign");category=category(owner);foreignCategory=category(other);
        party=partners.saveAndFlush(new BusinessPartner(owner,new BusinessPartnerProfile(PartnerPersonType.LEGAL_ENTITY,"12ABC34501DE35","Synthetic Party","",PartnerRole.BOTH,
                "","","","","","","","","","","","","","",true))).getId();
        center=centers.saveAndFlush(new ResultCenter(owner,"1",null,"Synthetic Center",true)).getId();
        createKey=UUID.randomUUID();entry=creator.create(manager.getId(),owner.getId(),createKey,createData(bankA,category,party,null)).entryId();
        foreignEntry=creator.create(otherManager.getId(),other.getId(),UUID.randomUUID(),createData(foreignBank,foreignCategory,null,null)).entryId();
    }
    @AfterEach void cleanSyntheticFixturesOnly() {
        for(var company:List.of(owner,other)) {
            for(var table:List.of("financial_entry_events","financial_entry_command_receipts","financial_entries","financial_company_settings",
                    "financial_bank_accounts","financial_categories","business_partners","operational_result_centers"))jdbc.update("DELETE FROM "+table+" WHERE company_id=?",company.getId());
            jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id IN (SELECT id FROM company_access_grants WHERE company_id=?)",company.getId());
            jdbc.update("DELETE FROM company_access_grants WHERE company_id=?",company.getId());jdbc.update("DELETE FROM companies WHERE id=?",company.getId());
        }
        for(var id:clientIds)jdbc.update("DELETE FROM clients WHERE id=?",id);for(var id:firmIds)jdbc.update("DELETE FROM accounting_firms WHERE id=?",id);for(var id:userIds)jdbc.update("DELETE FROM user_accounts WHERE id=?",id);
    }
    @Test void settlementAndReversalMustCommitExactStatesVersionsReceiptsAndAuditTogether() {
        var settled=settle(UUID.randomUUID(),0,"110.50",bankB);assertThat(settled.version()).isEqualTo(1);assertThat(settled.action()).isEqualTo(EntryCommandAction.SETTLED);
        var state=state();assertThat(state.nominalAmount()).isEqualByComparingTo("100.00");assertThat(state.actualPaidAmount()).isEqualByComparingTo("110.50");
        assertThat(state.paymentDate()).isEqualTo(paid);assertThat(state.bankAccountId()).isEqualTo(bankB);
        assertThat(jdbc.queryForObject("SELECT before_state::jsonb->>'nominalAmount' FROM financial_entry_events WHERE company_id=? AND action='SETTLED'",String.class,owner.getId())).isEqualTo("100.00");
        assertThat(jdbc.queryForObject("SELECT after_state::jsonb->>'actualPaidAmount' FROM financial_entry_events WHERE company_id=? AND action='SETTLED'",String.class,owner.getId())).isEqualTo("110.50");
        var reversed=reverse(UUID.randomUUID(),settled.version());assertThat(reversed.version()).isEqualTo(2);assertThat(reversed.action()).isEqualTo(EntryCommandAction.SETTLEMENT_REVERSED);
        assertThat(state().paymentDate()).isNull();assertThat(state().actualPaidAmount()).isNull();assertThat(state().bankAccountId()).isEqualTo(bankB);
        assertThat(count("financial_entry_events")).isEqualTo(3);assertThat(count("financial_entry_command_receipts")).isEqualTo(3);
    }
    @Test void applyingSettlementAgainWithTheNominalMustRemoveThePreviousPaidOverride() {
        var first=settle(UUID.randomUUID(),0,"90.00",bankB);
        service.settle(manager.getId(),owner.getId(),UUID.randomUUID(),entry,first.version(),new EntrySettlementData(paid.plusDays(1),new BigDecimal("100"),bankA));
        assertThat(state().actualPaidAmount()).isNull();assertThat(state().paymentDate()).isEqualTo(paid.plusDays(1));
        assertThat(state().bankAccountId()).isEqualTo(bankA);assertThat(state().effectiveAmount()).isEqualByComparingTo("100.00");
    }
    @Test void laterCatalogChangesAndNewCenterRequirementsMustNotBlockHistoricalSettlement() {
        var withCenter=creator.create(manager.getId(),owner.getId(),UUID.randomUUID(),createData(bankA,category,party,center));
        jdbc.update("UPDATE business_partners SET role='CUSTOMER' WHERE id=?",party);jdbc.update("UPDATE operational_result_centers SET active=false WHERE id=?",center);
        jdbc.update("UPDATE financial_bank_accounts SET active=false WHERE id=?",bankA);
        jdbc.update("INSERT INTO financial_company_settings(company_id,require_result_center) VALUES(?,true)",owner.getId());
        settle(UUID.randomUUID(),0,"101.00",bankA);
        service.settle(manager.getId(),owner.getId(),UUID.randomUUID(),withCenter.entryId(),0L,new EntrySettlementData(paid,new BigDecimal("101.00"),bankA));
        assertThat(state().resultCenterId()).isNull();assertThat(state().partnerId()).isEqualTo(party);
        var preserved=entries.findById(withCenter.entryId()).orElseThrow().getValues();assertThat(preserved.resultCenterId()).isEqualTo(center);assertThat(preserved.partnerId()).isEqualTo(party);
    }
    @Test void replayAfterReversalMustReturnTheOriginalReceiptWithoutReapplyingPaymentAndRequireCurrentPermission() {
        var key=UUID.randomUUID();var original=settle(key,0,"110.00",bankB);reverse(UUID.randomUUID(),original.version());
        assertThat(settle(key,0,"110.00",bankB)).isEqualTo(original);assertThat(state().paymentDate()).isNull();assertThat(count("financial_entry_events")).isEqualTo(3);
        jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id IN (SELECT id FROM company_access_grants WHERE company_id=? AND user_account_id=?)",owner.getId(),manager.getId());
        assertThatThrownBy(()->settle(key,0,"110.00",bankB)).isInstanceOf(com.finemp.administration.OrganizationAccessDeniedException.class);
    }
    @Test void changedPayloadAndKeysAlreadyUsedForCreateOrAnotherActionMustConflictWithoutNewEvents() {
        assertThatThrownBy(()->settle(createKey,0,"100.00",bankB)).isInstanceOf(FinancialEntryCommandConflictException.class);
        var key=UUID.randomUUID();settle(key,0,"110.00",bankB);
        assertThatThrownBy(()->settle(key,0,"110.01",bankB)).isInstanceOf(FinancialEntryCommandConflictException.class);
        assertThatThrownBy(()->reverse(key,0)).isInstanceOf(FinancialEntryCommandConflictException.class);
        assertThat(count("financial_entry_events")).isEqualTo(2);assertThat(count("financial_entry_command_receipts")).isEqualTo(2);
    }
    @Test void permissionsTargetsAndBanksMustRemainScopedToTheAuthorizedCompany() {
        for(var actor:List.of(viewer,restricted))assertThatThrownBy(()->service.settle(actor.getId(),owner.getId(),UUID.randomUUID(),entry,0L,new EntrySettlementData(paid,BigDecimal.ONE,bankA)))
                .isInstanceOf(com.finemp.administration.OrganizationAccessDeniedException.class);
        assertThatThrownBy(()->settle(UUID.randomUUID(),0,"100",foreignBank)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.reverse(manager.getId(),owner.getId(),UUID.randomUUID(),foreignEntry,0L)).isInstanceOf(com.finemp.administration.SaasResourceNotFoundException.class);
        assertThatThrownBy(()->service.reverse(manager.getId(),other.getId(),UUID.randomUUID(),foreignEntry,0L)).isInstanceOf(com.finemp.administration.OrganizationAccessDeniedException.class);
        assertThat(state().paymentDate()).isNull();assertThat(entries.findById(foreignEntry).orElseThrow().getValues().paymentDate()).isNull();assertThat(count("financial_entry_events")).isEqualTo(1);
    }
    @Test void failureAfterWritingTheEntryAndAuditMustRollbackEverythingAndAllowTheSameKeyToRetry() {
        var key=UUID.randomUUID();
        assertThatThrownBy(()->new TransactionTemplate(transactions).execute(status->{
            jdbc.execute("ALTER TABLE financial_entry_command_receipts ADD CONSTRAINT synthetic_settlement_receipt_failure CHECK(command_id <> '"+key+"'::uuid)");
            return settle(key,0,"110.00",bankB);
        })).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(state().paymentDate()).isNull();assertThat(entries.findById(entry).orElseThrow().getVersion()).isZero();
        assertThat(count("financial_entry_events")).isEqualTo(1);assertThat(count("financial_entry_command_receipts")).isEqualTo(1);
        assertThat(settle(key,0,"110.00",bankB).version()).isEqualTo(1);
    }
    @Test void concurrentIdenticalCommandsMustProduceOnlyOneSettlementEventAndReceipt()throws Exception {
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);var key=UUID.randomUUID();
        try {
            Callable<FinancialEntryCommandReceipt> task=()->{await(start);return settle(key,0,"110.00",bankB);};
            var first=pool.submit(task);var second=pool.submit(task);start.countDown();assertThat(first.get(15,TimeUnit.SECONDS)).isEqualTo(second.get(15,TimeUnit.SECONDS));
            assertThat(count("financial_entry_events")).isEqualTo(2);assertThat(count("financial_entry_command_receipts")).isEqualTo(2);
        }finally {start.countDown();pool.shutdownNow();}
    }
    @Test void competingCommandsWithTheSameVersionMustAllowOnlyOneCommittedTransition()throws Exception {
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            Callable<Boolean> task=()->{await(start);try {settle(UUID.randomUUID(),0,"110.00",bankB);return true;}
                catch(FinancialEntryCommandConflictException conflict) {assertThat(conflict.getReason()).isEqualTo(FinancialEntryCommandConflictException.Reason.STALE_VERSION);return false;}};
            var first=pool.submit(task);var second=pool.submit(task);start.countDown();assertThat(List.of(first.get(15,TimeUnit.SECONDS),second.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
            assertThat(count("financial_entry_events")).isEqualTo(2);assertThat(entries.findById(entry).orElseThrow().getVersion()).isEqualTo(1);
        }finally {start.countDown();pool.shutdownNow();}
    }
    @Test void staleManagedStateAndUnsupportedAmbientIsolationMustFailBeforeChangingPayment()throws Exception {
        var pool=Executors.newSingleThreadExecutor();
        try {
            assertThatThrownBy(()->new TransactionTemplate(transactions).execute(status->{
                assertThat(entries.findById(entry).orElseThrow().getVersion()).isZero();
                try {pool.submit(()->settle(UUID.randomUUID(),0,"110.00",bankB)).get(10,TimeUnit.SECONDS);}
                catch(Exception failure) {throw new IllegalStateException("Synthetic concurrent settlement failed",failure);}
                return reverse(UUID.randomUUID(),1);
            })).isInstanceOf(FinancialEntryCommandConflictException.class);
            assertThat(state().paymentDate()).isEqualTo(paid);assertThat(count("financial_entry_events")).isEqualTo(2);
            var repeatable=new TransactionTemplate(transactions);repeatable.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
            assertThatThrownBy(()->repeatable.execute(status->reverse(UUID.randomUUID(),1))).isInstanceOf(FinancialEntryCommandConflictException.class);
        }finally {pool.shutdownNow();}
    }
    @Test void reversingAnAlreadyOpenEntryMustRecordTheCommandWithoutInventingAStateChangeRequirement() {
        var receipt=reverse(UUID.randomUUID(),0);assertThat(receipt.action()).isEqualTo(EntryCommandAction.SETTLEMENT_REVERSED);
        assertThat(state().paymentDate()).isNull();assertThat(state().bankAccountId()).isEqualTo(bankA);
        assertThat(receipt.version()).isEqualTo(entries.findById(entry).orElseThrow().getVersion());
        assertThat(count("financial_entry_events")).isEqualTo(2);assertThat(count("financial_entry_command_receipts")).isEqualTo(2);
    }
    FinancialEntryCommandReceipt settle(UUID key,long version,String amount,UUID bank) {return service.settle(manager.getId(),owner.getId(),key,entry,version,new EntrySettlementData(paid,new BigDecimal(amount),bank));}
    FinancialEntryCommandReceipt reverse(UUID key,long version) {return service.reverse(manager.getId(),owner.getId(),key,entry,version);}
    FinancialEntryValues state() {return entries.findById(entry).orElseThrow().getValues();}
    long count(String table) {return jdbc.queryForObject("SELECT count(*) FROM "+table+" WHERE company_id=?",Long.class,owner.getId());}
    void await(CountDownLatch start)throws InterruptedException {if(!start.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Synthetic barrier");}
    FinancialEntryCommandData createData(UUID bank,UUID category,UUID partner,UUID center) {return new FinancialEntryCommandData(FinancialDirection.EXPENSE,"Synthetic settlement entry",new BigDecimal("100.00"),date,date,YearMonth.of(2026,7),date,null,bank,category,partner,center,null,"","SYNTHETIC","");}
    UUID bank(Company company,String label) {return banks.saveAndFlush(new BankAccount(company,"Synthetic "+label,BankAccountType.CASH,BigDecimal.ZERO,date,true)).getId();}
    UUID category(Company company) {var group=categories.saveAndFlush(FinancialCategory.group(company,FinancialDirection.EXPENSE,1,"Synthetic Group"));return categories.saveAndFlush(FinancialCategory.subcategory(group,1,"Synthetic Category")).getId();}
    Company company(String label,String cnpj) {var firm=firms.saveAndFlush(new AccountingFirm("Synthetic Settlement Firm "+label,true));firmIds.add(firm.getId());var client=clients.saveAndFlush(new Client(firm,"Synthetic Settlement Client "+label,true));clientIds.add(client.getId());return companies.saveAndFlush(new Company(client,"Synthetic Settlement Company "+label,cnpj));}
    UserAccount user(String label) {var actor=users.saveAndFlush(new UserAccount("Synthetic Settlement "+label,UUID.randomUUID()+"@example.invalid","hash"));userIds.add(actor.getId());return actor;}
    void grant(UserAccount actor,Company company,CostCenterScope scope,CompanyPermission permission) {grants.saveAndFlush(new CompanyAccessGrant(actor,company,CompanyAccessRole.CUSTOM,scope,Set.of(permission)));}
}
