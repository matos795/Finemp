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
class MonthlyEntryIntegrationTest {
    @Autowired MonthlyEntryCommandService service;@Autowired FinancialEntryCommandService individual;@Autowired EntryDeletionCommandService deletion;
    @Autowired FinancialEntryRepository entries;@Autowired FinancialEntrySeriesRepository series;@Autowired EntrySeriesMembershipRepository members;
    @Autowired BankAccountRepository banks;@Autowired FinancialCategoryRepository categories;@Autowired CompanyRepository companies;@Autowired ClientRepository clients;@Autowired AccountingFirmRepository firms;
    @Autowired UserAccountRepository users;@Autowired CompanyAccessGrantRepository grants;@Autowired JdbcTemplate jdbc;@Autowired PlatformTransactionManager transactions;
    Company owner,other;UserAccount manager,viewer,restricted;UUID bank,category,foreignBank;
    final List<UUID> firmIds=new ArrayList<>(),clientIds=new ArrayList<>(),userIds=new ArrayList<>();
    @BeforeEach void prepare() {
        owner=company("Owner","93939393000193");other=company("Other","94949494000194");manager=createUser("Manager");viewer=createUser("Viewer");restricted=createUser("Restricted");
        grant(manager,CostCenterScope.ALL,CompanyPermission.FINANCIAL_ENTRY_MANAGE);grant(viewer,CostCenterScope.ALL,CompanyPermission.FINANCIAL_ENTRY_VIEW);grant(restricted,CostCenterScope.RESTRICTED,CompanyPermission.FINANCIAL_ENTRY_MANAGE);
        bank=bank(owner);foreignBank=bank(other);var group=categories.saveAndFlush(FinancialCategory.group(owner,FinancialDirection.EXPENSE,1,"Synthetic Monthly Group"));category=categories.saveAndFlush(FinancialCategory.subcategory(group,1,"Synthetic Monthly Category")).getId();
    }
    @AfterEach void cleanSyntheticFixtures() {
        for(var company:List.of(owner,other)) {
            for(var table:List.of("financial_entry_series_members","financial_entry_series","financial_entry_events","financial_entry_series_command_receipts","financial_entry_command_receipts","financial_entry_command_keys","financial_entries","financial_company_settings","financial_bank_accounts","financial_categories"))jdbc.update("DELETE FROM "+table+" WHERE company_id=?",company.getId());
            jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id IN (SELECT id FROM company_access_grants WHERE company_id=?)",company.getId());
            jdbc.update("DELETE FROM company_access_grants WHERE company_id=?",company.getId());jdbc.update("DELETE FROM companies WHERE id=?",company.getId());
        }
        for(var id:clientIds)jdbc.update("DELETE FROM clients WHERE id=?",id);for(var id:firmIds)jdbc.update("DELETE FROM accounting_firms WHERE id=?",id);for(var id:userIds)jdbc.update("DELETE FROM user_accounts WHERE id=?",id);
    }
    @Test void monthlyCreationMustRepeatExactMoneyAndPreserveOriginalDateAndPaymentSemantics() {
        var receipt=create(UUID.randomUUID(),3,data(bank,"1.23"));assertThat(receipt.items()).hasSize(3);
        var rows=receipt.items().stream().map(item->entries.findById(item.entryId()).orElseThrow()).toList();
        assertThat(rows).extracting(row->row.getValues().dueDate()).containsExactly(LocalDate.of(2026,1,31),LocalDate.of(2026,2,28),LocalDate.of(2026,3,31));
        assertThat(rows).extracting(row->row.getValues().documentDate()).containsExactly(LocalDate.of(2025,12,30),LocalDate.of(2026,1,30),LocalDate.of(2026,2,28));
        assertThat(rows).extracting(row->row.getValues().competence()).containsExactly(YearMonth.of(2025,11),YearMonth.of(2025,12),YearMonth.of(2026,1));
        assertThat(rows.getFirst().getValues().paymentDate()).isEqualTo(LocalDate.of(2026,2,2));assertThat(rows.subList(1,3)).allSatisfy(row->assertThat(row.getValues().paymentDate()).isNull());
        assertThat(rows).allSatisfy(row->{assertThat(row.getValues().nominalAmount()).isEqualTo(new BigDecimal("1.23"));assertThat(row.getValues().entryDate()).isEqualTo(LocalDate.of(2026,2,5));assertThat(row.getValues().description()).isEqualTo("Synthetic monthly");});
        assertThat(members.findByCompanyIdAndSeriesIdOrderByNumber(owner.getId(),receipt.seriesId())).extracting(EntrySeriesMembership::getNumber).containsExactly(1,2,3);
        assertThat(count("financial_entry_events")).isEqualTo(3);assertThat(count("financial_entry_series_command_receipts")).isEqualTo(1);assertThat(count("financial_entry_command_keys")).isEqualTo(1);
        assertThat(count("financial_entry_command_receipts")).isZero();
    }
    @Test void replayMustReturnOriginalReceiptWithoutRevalidatingChangedReferencesAndStillRequireCurrentPermission() {
        var key=UUID.randomUUID();var original=create(key,2,data(bank,"1.23"));
        var removed=original.items().getLast().entryId();
        jdbc.update("DELETE FROM financial_entry_series_members WHERE company_id=? AND entry_id=?",owner.getId(),removed);
        jdbc.update("DELETE FROM financial_entries WHERE company_id=? AND id=?",owner.getId(),removed);
        jdbc.update("UPDATE financial_bank_accounts SET active=false WHERE id=?",bank);jdbc.update("INSERT INTO financial_company_settings(company_id,require_result_center) VALUES(?,true)",owner.getId());
        assertThat(create(key,2,data(bank,"1.23"))).isEqualTo(original);assertThat(count("financial_entries")).isEqualTo(1);
        jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id IN (SELECT id FROM company_access_grants WHERE company_id=? AND user_account_id=?)",owner.getId(),manager.getId());
        assertThatThrownBy(()->create(key,2,data(bank,"1.23"))).isInstanceOf(OrganizationAccessDeniedException.class);
    }
    @Test void commandIdentityMustBeSharedWithIndividualActionsAndRejectChangedContentOrQuantity() {
        var individualKey=UUID.randomUUID();individual.create(manager.getId(),owner.getId(),individualKey,data(bank,"1.23"));
        assertThatThrownBy(()->create(individualKey,2,data(bank,"1.23"))).isInstanceOf(FinancialEntryCommandConflictException.class);
        var key=UUID.randomUUID();var receipt=create(key,2,data(bank,"1.23"));
        assertThatThrownBy(()->create(key,3,data(bank,"1.23"))).isInstanceOf(FinancialEntryCommandConflictException.class);
        assertThatThrownBy(()->create(key,2,data(bank,"1.24"))).isInstanceOf(FinancialEntryCommandConflictException.class);
        assertThatThrownBy(()->individual.create(manager.getId(),owner.getId(),key,data(bank,"1.23"))).isInstanceOf(FinancialEntryCommandConflictException.class);
        assertThatThrownBy(()->deletion.delete(manager.getId(),owner.getId(),key,receipt.items().getFirst().entryId(),0L)).isInstanceOf(FinancialEntryCommandConflictException.class);
        assertThat(count("financial_entries")).isEqualTo(3);assertThat(count("financial_entry_command_keys")).isEqualTo(2);
    }
    @Test void failureAtTheFinalReceiptMustRollbackGroupMembersAllEntriesEventsAndKeyThenPermitRetry() {
        var key=UUID.randomUUID();
        assertThatThrownBy(()->new TransactionTemplate(transactions).execute(status->{
            jdbc.execute("ALTER TABLE financial_entry_series_command_receipts ADD CONSTRAINT synthetic_monthly_receipt_failure CHECK(command_id <> '"+key+"'::uuid)");
            return create(key,3,data(bank,"1.23"));
        })).isInstanceOf(DataIntegrityViolationException.class);
        empty();assertThat(create(key,3,data(bank,"1.23")).items()).hasSize(3);
    }
    @Test void permissionAndBothCompaniesReferencesMustStayIsolatedAndBadCalendarsMustPersistNothing() {
        for(var actor:List.of(viewer,restricted))assertThatThrownBy(()->service.create(actor.getId(),owner.getId(),UUID.randomUUID(),2,data(bank,"1.23"))).isInstanceOf(OrganizationAccessDeniedException.class);
        assertThatThrownBy(()->service.create(manager.getId(),other.getId(),UUID.randomUUID(),2,data(foreignBank,"1.23"))).isInstanceOf(OrganizationAccessDeniedException.class);
        assertThatThrownBy(()->create(UUID.randomUUID(),2,data(foreignBank,"1.23"))).isInstanceOf(IllegalArgumentException.class);
        var base=data(bank,"1.23");var overflow=new FinancialEntryCommandData(base.direction(),base.description(),base.nominalAmount(),base.documentDate(),base.entryDate(),YearMonth.of(9999,12),base.dueDate(),null,bank,category,null,null,null,"","","");
        assertThatThrownBy(()->create(UUID.randomUUID(),2,overflow)).isInstanceOf(IllegalArgumentException.class);empty();
    }
    @Test void concurrentIdenticalCreationMustCommitOnlyOneCompleteSet()throws Exception {
        var key=UUID.randomUUID();var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            Callable<EntrySeriesCommandReceipt> task=()->{if(!start.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Synthetic barrier");return create(key,3,data(bank,"1.23"));};
            var a=pool.submit(task);var b=pool.submit(task);start.countDown();assertThat(a.get(15,TimeUnit.SECONDS)).isEqualTo(b.get(15,TimeUnit.SECONDS));
            assertThat(count("financial_entries")).isEqualTo(3);assertThat(count("financial_entry_events")).isEqualTo(3);assertThat(count("financial_entry_command_keys")).isEqualTo(1);
        }finally {start.countDown();pool.shutdownNow();}
    }
    @Test void concurrentIndividualAndMonthlyCommandsWithOneKeyMustHaveOnlyOneWinner()throws Exception {
        var key=UUID.randomUUID();var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            var batch=pool.submit(()->{start.await(5,TimeUnit.SECONDS);try {create(key,2,data(bank,"1.23"));return true;}catch(FinancialEntryCommandConflictException conflict) {return false;}});
            var single=pool.submit(()->{start.await(5,TimeUnit.SECONDS);try {individual.create(manager.getId(),owner.getId(),key,data(bank,"1.23"));return true;}catch(FinancialEntryCommandConflictException conflict) {return false;}});
            start.countDown();assertThat(List.of(batch.get(15,TimeUnit.SECONDS),single.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
            assertThat(count("financial_entry_command_keys")).isEqualTo(1);assertThat(count("financial_entry_series_command_receipts")+count("financial_entry_command_receipts")).isEqualTo(1);
            assertThat(count("financial_entry_events")).isEqualTo(count("financial_entries"));
        }finally {start.countDown();pool.shutdownNow();}
    }
    @Test void databaseMustRejectReceiptTypesThatDoNotOwnTheCommandKey() {
        var key=UUID.randomUUID();var receipt=create(key,2,data(bank,"1.23"));
        new TransactionTemplate(transactions).executeWithoutResult(status->{
            assertThatThrownBy(()->jdbc.update("INSERT INTO financial_entry_command_receipts(company_id,actor_id,command_id,fingerprint,entry_id,action,result_version,committed_at) VALUES(?,?,?,?,?,'CREATED',0,CURRENT_TIMESTAMP)",owner.getId(),manager.getId(),key,"f".repeat(64),receipt.items().getFirst().entryId())).isInstanceOf(DataIntegrityViolationException.class);status.setRollbackOnly();
        });
        assertThat(count("financial_entry_command_receipts")).isZero();assertThat(count("financial_entry_series_command_receipts")).isEqualTo(1);
    }
    @Test void boundedLargeSetMustKeepExactAmountsAndUnsupportedAmbientIsolationMustFail() {
        var receipt=create(UUID.randomUUID(),120,data(bank,"99999999999999999.99"));assertThat(receipt.items()).hasSize(120);
        assertThat(entries.findById(receipt.items().getLast().entryId()).orElseThrow().getValues().nominalAmount()).isEqualTo(new BigDecimal("99999999999999999.99"));
        var repeatable=new TransactionTemplate(transactions);repeatable.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        assertThatThrownBy(()->repeatable.execute(status->create(UUID.randomUUID(),2,data(bank,"1.23")))).isInstanceOf(FinancialEntryCommandConflictException.class);
        assertThat(count("financial_entries")).isEqualTo(120);
    }
    EntrySeriesCommandReceipt create(UUID key,int quantity,FinancialEntryCommandData data) {return service.create(manager.getId(),owner.getId(),key,quantity,data);}
    FinancialEntryCommandData data(UUID bank,String amount) {return new FinancialEntryCommandData(FinancialDirection.EXPENSE,"Synthetic monthly",new BigDecimal(amount),LocalDate.of(2025,12,30),LocalDate.of(2026,2,5),YearMonth.of(2025,11),LocalDate.of(2026,1,31),LocalDate.of(2026,2,2),bank,category,null,null,null,"","","");}
    void empty() {for(var table:List.of("financial_entry_series_members","financial_entry_series","financial_entries","financial_entry_events","financial_entry_command_keys","financial_entry_series_command_receipts"))assertThat(count(table)).isZero();}
    long count(String table) {return jdbc.queryForObject("SELECT count(*) FROM "+table+" WHERE company_id=?",Long.class,owner.getId());}
    UUID bank(Company company) {return banks.saveAndFlush(new BankAccount(company,"Synthetic Monthly Bank",BankAccountType.CASH,BigDecimal.ZERO,LocalDate.of(2026,1,1),true)).getId();}
    Company company(String label,String cnpj) {var firm=firms.saveAndFlush(new AccountingFirm("Synthetic Monthly Firm "+label,true));firmIds.add(firm.getId());var client=clients.saveAndFlush(new Client(firm,"Synthetic Monthly Client "+label,true));clientIds.add(client.getId());return companies.saveAndFlush(new Company(client,"Synthetic Monthly Company "+label,cnpj));}
    UserAccount createUser(String label) {var actor=users.saveAndFlush(new UserAccount("Synthetic Monthly "+label,UUID.randomUUID()+"@example.invalid","hash"));userIds.add(actor.getId());return actor;}
    void grant(UserAccount actor,CostCenterScope scope,CompanyPermission permission) {grants.saveAndFlush(new CompanyAccessGrant(actor,owner,CompanyAccessRole.CUSTOM,scope,Set.of(permission)));}
}
