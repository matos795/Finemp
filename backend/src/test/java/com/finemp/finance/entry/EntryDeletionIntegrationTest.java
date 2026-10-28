package com.finemp.finance.entry;

import com.finemp.TestcontainersConfiguration;
import com.finemp.access.*;
import com.finemp.accountingfirm.*;
import com.finemp.administration.*;
import com.finemp.client.*;
import com.finemp.company.*;
import com.finemp.finance.bank.*;
import com.finemp.finance.category.*;
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
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties="finemp.finance.entries.enabled=true")
@Import(TestcontainersConfiguration.class)
class EntryDeletionIntegrationTest {
    @Autowired EntryDeletionCommandService service;@Autowired FinancialEntryCommandService creator;@Autowired EntrySettlementCommandService settlement;
    @Autowired FinancialEntryRepository entries;@Autowired BankAccountRepository banks;@Autowired FinancialCategoryRepository categories;
    @Autowired CompanyRepository companies;@Autowired ClientRepository clients;@Autowired AccountingFirmRepository firms;
    @Autowired UserAccountRepository users;@Autowired CompanyAccessGrantRepository grants;@Autowired JdbcTemplate jdbc;@Autowired PlatformTransactionManager transactions;
    Company owner,other;UserAccount manager,viewer,restricted;UUID bank,category,entry,foreignEntry,createKey;
    final LocalDate date=LocalDate.of(2026,7,1);
    final List<UUID> firmIds=new ArrayList<>(),clientIds=new ArrayList<>(),userIds=new ArrayList<>();
    @BeforeEach void prepare() {
        owner=company("Owner","87878787000187");other=company("Other","88888888000188");manager=createUser("Manager");viewer=createUser("Viewer");restricted=createUser("Restricted");
        grant(manager,CostCenterScope.ALL,CompanyPermission.FINANCIAL_ENTRY_MANAGE);grant(viewer,CostCenterScope.ALL,CompanyPermission.FINANCIAL_ENTRY_VIEW);grant(restricted,CostCenterScope.RESTRICTED,CompanyPermission.FINANCIAL_ENTRY_MANAGE);
        bank=bank(owner);category=category(owner);createKey=UUID.randomUUID();entry=creator.create(manager.getId(),owner.getId(),createKey,data()).entryId();
        var foreignBank=bank(other);var foreignCategory=category(other);
        foreignEntry=entries.saveAndFlush(new FinancialEntry(other,new FinancialEntryValues(FinancialDirection.EXPENSE,"Synthetic foreign deletion",BigDecimal.ONE,date,date,YearMonth.of(2026,7),date,null,null,foreignBank,foreignCategory,null,null,null,"","",""),manager.getId())).getId();
    }
    @AfterEach void cleanSyntheticFixtures() {
        for(var company:List.of(owner,other)) {
            for(var table:List.of("financial_entry_events","financial_entry_command_receipts","financial_entry_command_keys","financial_entries","financial_bank_accounts","financial_categories"))jdbc.update("DELETE FROM "+table+" WHERE company_id=?",company.getId());
            jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id IN (SELECT id FROM company_access_grants WHERE company_id=?)",company.getId());
            jdbc.update("DELETE FROM company_access_grants WHERE company_id=?",company.getId());jdbc.update("DELETE FROM companies WHERE id=?",company.getId());
        }
        for(var id:clientIds)jdbc.update("DELETE FROM clients WHERE id=?",id);for(var id:firmIds)jdbc.update("DELETE FROM accounting_firms WHERE id=?",id);for(var id:userIds)jdbc.update("DELETE FROM user_accounts WHERE id=?",id);
    }
    @Test void paidIndividualDeletionMustPreserveReceiptsAndBeforeStateWithoutInventingAPosteriorResource() {
        var receipt=delete(UUID.randomUUID(),0);assertThat(receipt.action()).isEqualTo(EntryCommandAction.DELETED);assertThat(receipt.version()).isZero();
        assertThat(entries.findById(entry)).isEmpty();assertThat(count("financial_entry_events")).isEqualTo(2);assertThat(count("financial_entry_command_receipts")).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT before_state::jsonb->>'nominalAmount' FROM financial_entry_events WHERE company_id=? AND action='DELETED'",String.class,owner.getId())).isEqualTo("1.23");
        assertThat(jdbc.queryForObject("SELECT after_state IS NULL AND after_version IS NULL FROM financial_entry_events WHERE company_id=? AND action='DELETED'",Boolean.class,owner.getId())).isTrue();
        assertThat(entries.existsByCompanyIdAndValuesBankAccountId(owner.getId(),bank)).isFalse();assertThat(banks.findById(bank)).isPresent();
    }
    @Test void deletionReplayAndOriginalCreateReplayMustSurviveRemovalButRequireCurrentPermission() {
        var key=UUID.randomUUID();var receipt=delete(key,0);assertThat(delete(key,0)).isEqualTo(receipt);
        assertThat(creator.create(manager.getId(),owner.getId(),createKey,data()).entryId()).isEqualTo(entry);assertThat(entries.findById(entry)).isEmpty();assertThat(count("financial_entry_events")).isEqualTo(2);
        jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id IN (SELECT id FROM company_access_grants WHERE company_id=? AND user_account_id=?)",owner.getId(),manager.getId());
        assertThatThrownBy(()->delete(key,0)).isInstanceOf(OrganizationAccessDeniedException.class);
    }
    @Test void keysAreSharedWithOtherEntryActionsAndExpectedVersionParticipatesInFingerprint() {
        assertThatThrownBy(()->delete(createKey,0)).isInstanceOf(FinancialEntryCommandConflictException.class);
        var key=UUID.randomUUID();delete(key,0);
        assertThatThrownBy(()->delete(key,1)).isInstanceOf(FinancialEntryCommandConflictException.class);
        assertThatThrownBy(()->settlement.reverse(manager.getId(),owner.getId(),key,entry,0L)).isInstanceOf(FinancialEntryCommandConflictException.class);
        assertThat(count("financial_entry_events")).isEqualTo(2);
    }
    @Test void permissionsAndForeignTargetsMustRemainIsolatedAcrossCompaniesClientsAndFirms() {
        for(var actor:List.of(viewer,restricted))assertThatThrownBy(()->service.delete(actor.getId(),owner.getId(),UUID.randomUUID(),entry,0L)).isInstanceOf(OrganizationAccessDeniedException.class);
        assertThatThrownBy(()->service.delete(manager.getId(),owner.getId(),UUID.randomUUID(),foreignEntry,0L)).isInstanceOf(SaasResourceNotFoundException.class);
        assertThatThrownBy(()->service.delete(manager.getId(),other.getId(),UUID.randomUUID(),foreignEntry,0L)).isInstanceOf(OrganizationAccessDeniedException.class);
        assertThat(entries.findById(foreignEntry)).isPresent();assertThat(entries.findById(entry)).isPresent();assertThat(count("financial_entry_events")).isEqualTo(1);
    }
    @Test void failureAfterDeletionAndEventMustRollbackStateAndHistoryThenAllowSameKeyRetry() {
        var key=UUID.randomUUID();
        assertThatThrownBy(()->new TransactionTemplate(transactions).execute(status->{
            jdbc.execute("ALTER TABLE financial_entry_command_receipts ADD CONSTRAINT synthetic_entry_deletion_failure CHECK(command_id <> '"+key+"'::uuid)");
            return delete(key,0);
        })).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(entries.findById(entry)).isPresent();assertThat(count("financial_entry_events")).isEqualTo(1);assertThat(count("financial_entry_command_receipts")).isEqualTo(1);
        assertThat(delete(key,0).action()).isEqualTo(EntryCommandAction.DELETED);
    }
    @Test void concurrentIdenticalDeletionMustProduceOnlyOneRemovalEventAndReceipt()throws Exception {
        var key=UUID.randomUUID();var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            Callable<FinancialEntryCommandReceipt> task=()->{if(!start.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Synthetic barrier");return delete(key,0);};
            var first=pool.submit(task);var second=pool.submit(task);start.countDown();assertThat(first.get(15,TimeUnit.SECONDS)).isEqualTo(second.get(15,TimeUnit.SECONDS));
            assertThat(count("financial_entry_events")).isEqualTo(2);assertThat(count("financial_entry_command_receipts")).isEqualTo(2);assertThat(entries.findById(entry)).isEmpty();
        }finally {start.countDown();pool.shutdownNow();}
    }
    @Test void staleExpectedManagedStateAndUnsupportedIsolationMustNotRemoveTheCurrentEntry() {
        jdbc.update("UPDATE financial_entries SET version=1 WHERE id=?",entry);assertThatThrownBy(()->delete(UUID.randomUUID(),0)).isInstanceOf(FinancialEntryCommandConflictException.class);
        assertThatThrownBy(()->new TransactionTemplate(transactions).execute(status->{
            assertThat(entries.findById(entry).orElseThrow().getVersion()).isEqualTo(1);jdbc.update("UPDATE financial_entries SET version=2 WHERE id=?",entry);return delete(UUID.randomUUID(),2);
        })).isInstanceOf(FinancialEntryCommandConflictException.class);
        var repeatable=new TransactionTemplate(transactions);repeatable.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        assertThatThrownBy(()->repeatable.execute(status->delete(UUID.randomUUID(),1))).isInstanceOf(FinancialEntryCommandConflictException.class);
        assertThat(entries.findById(entry).orElseThrow().getVersion()).isEqualTo(1);assertThat(count("financial_entry_events")).isEqualTo(1);
    }
    @Test void transitionConstraintsMustStillRequirePosteriorStateAndVersionForExistingActions() {
        for(var assignment:List.of("after_state=NULL","after_version=NULL"))new TransactionTemplate(transactions).executeWithoutResult(status->{
            assertThatThrownBy(()->jdbc.update("UPDATE financial_entry_events SET "+assignment+" WHERE company_id=? AND action='CREATED'",owner.getId())).isInstanceOf(DataIntegrityViolationException.class);status.setRollbackOnly();
        });
        assertThat(count("financial_entry_events")).isEqualTo(1);
    }
    FinancialEntryCommandReceipt delete(UUID key,long version) {return service.delete(manager.getId(),owner.getId(),key,entry,version);}
    FinancialEntryCommandData data() {return new FinancialEntryCommandData(FinancialDirection.EXPENSE,"Synthetic paid deletion",new BigDecimal("1.23"),date,date,YearMonth.of(2026,7),date,date,bank,category,null,null,null,"","","");}
    long count(String table) {return jdbc.queryForObject("SELECT count(*) FROM "+table+" WHERE company_id=?",Long.class,owner.getId());}
    UUID bank(Company company) {return banks.saveAndFlush(new BankAccount(company,"Synthetic Deletion Bank",BankAccountType.CASH,BigDecimal.ZERO,date,true)).getId();}
    UUID category(Company company) {var group=categories.saveAndFlush(FinancialCategory.group(company,FinancialDirection.EXPENSE,1,"Synthetic Deletion Group"));return categories.saveAndFlush(FinancialCategory.subcategory(group,1,"Synthetic Deletion Category")).getId();}
    Company company(String label,String cnpj) {var firm=firms.saveAndFlush(new AccountingFirm("Synthetic Deletion Firm "+label,true));firmIds.add(firm.getId());var client=clients.saveAndFlush(new Client(firm,"Synthetic Deletion Client "+label,true));clientIds.add(client.getId());return companies.saveAndFlush(new Company(client,"Synthetic Deletion Company "+label,cnpj));}
    UserAccount createUser(String label) {var actor=users.saveAndFlush(new UserAccount("Synthetic Deletion "+label,UUID.randomUUID()+"@example.invalid","hash"));userIds.add(actor.getId());return actor;}
    void grant(UserAccount actor,CostCenterScope scope,CompanyPermission permission) {grants.saveAndFlush(new CompanyAccessGrant(actor,owner,CompanyAccessRole.CUSTOM,scope,Set.of(permission)));}
}
