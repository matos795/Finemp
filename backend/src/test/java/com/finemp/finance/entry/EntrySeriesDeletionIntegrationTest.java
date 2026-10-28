package com.finemp.finance.entry;

import com.finemp.TestcontainersConfiguration;
import com.finemp.access.*;
import com.finemp.accountingfirm.*;
import com.finemp.administration.SaasResourceNotFoundException;
import com.finemp.client.*;
import com.finemp.company.*;
import com.finemp.finance.bank.*;
import com.finemp.finance.category.*;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"finemp.finance.entries.enabled=true","finemp.finance.entries.commands.enabled=true"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EntrySeriesDeletionIntegrationTest {
    @Autowired MonthlyEntryCommandService monthly;@Autowired EntryDeletionCommandService deletion;@Autowired FinancialEntryCommandService individual;@Autowired EntrySettlementCommandService settlement;
    @Autowired FinancialEntryRepository entries;@Autowired EntrySeriesMembershipRepository members;@Autowired FinancialEntrySeriesRepository series;
    @Autowired CompanyRepository companies;@Autowired ClientRepository clients;@Autowired AccountingFirmRepository firms;@Autowired BankAccountRepository banks;@Autowired FinancialCategoryRepository categories;
    @Autowired UserAccountRepository users;@Autowired CompanyAccessGrantRepository grants;@Autowired JdbcTemplate jdbc;@Autowired PlatformTransactionManager transactions;@Autowired MockMvc mvc;
    Company owner,other;UserAccount manager,otherManager;UUID bank,category;EntrySeriesCommandReceipt original,foreign;
    final List<UUID> firmIds=new ArrayList<>(),clientIds=new ArrayList<>(),userIds=new ArrayList<>();
    @BeforeEach void prepare() {
        owner=company("Owner","95959595000195");other=company("Other","96969696000196");manager=createUser("Manager");otherManager=createUser("Other manager");grant(manager,owner);grant(otherManager,other);
        bank=bank(owner);category=category(owner);original=monthly.create(manager.getId(),owner.getId(),UUID.randomUUID(),5,data(bank,category));
        foreign=monthly.create(otherManager.getId(),other.getId(),UUID.randomUUID(),2,data(bank(other),category(other)));
    }
    @AfterEach void cleanSyntheticFixtures() {
        for(var company:List.of(owner,other)) {
            for(var table:List.of("financial_entry_series_members","financial_entry_series","financial_entry_events","financial_entry_series_command_receipts","financial_entry_command_receipts","financial_entry_command_keys","financial_entries","financial_bank_accounts","financial_categories"))jdbc.update("DELETE FROM "+table+" WHERE company_id=?",company.getId());
            jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id IN (SELECT id FROM company_access_grants WHERE company_id=?)",company.getId());
            jdbc.update("DELETE FROM company_access_grants WHERE company_id=?",company.getId());jdbc.update("DELETE FROM companies WHERE id=?",company.getId());
        }
        for(var id:clientIds)jdbc.update("DELETE FROM clients WHERE id=?",id);for(var id:firmIds)jdbc.update("DELETE FROM accounting_firms WHERE id=?",id);for(var id:userIds)jdbc.update("DELETE FROM user_accounts WHERE id=?",id);
    }
    @Test void selectionMustRemovePaidRootAndOnlyStrictlyLaterUnpaidOccurrencesWithoutRenumbering() {
        settlement.settle(manager.getId(),owner.getId(),UUID.randomUUID(),id(3),0L,new EntrySettlementData(LocalDate.of(2026,4,1),new BigDecimal("1.23"),bank));
        moveDue(4,LocalDate.of(2026,1,31));moveDue(5,LocalDate.of(2025,12,31));long before=count("financial_entry_events");
        var receipt=remove(UUID.randomUUID(),1,0);assertThat(receipt.deletedEntries()).extracting(FinancialEntryCommandReceipt.DeletedEntry::entryId).containsExactly(id(1),id(2));
        assertThat(members.findByCompanyIdAndSeriesIdOrderByNumber(owner.getId(),original.seriesId())).extracting(EntrySeriesMembership::getNumber).containsExactly(3,4,5);
        assertThat(members.findByCompanyIdAndSeriesIdOrderByNumber(owner.getId(),original.seriesId())).allSatisfy(item->assertThat(item.getQuantity()).isEqualTo(5));
        assertThat(count("financial_entry_events")).isEqualTo(before+2);assertThat(entries.findById(id(3))).isPresent();
    }
    @Test void editedDueDatesMustDetermineTheSetRatherThanOriginalPosition() {
        moveDue(3,LocalDate.of(2026,1,15));var receipt=remove(UUID.randomUUID(),3,1);
        assertThat(receipt.deletedEntries()).extracting(FinancialEntryCommandReceipt.DeletedEntry::entryId).containsExactly(id(3),id(2),id(4),id(5));
        assertThat(members.findByCompanyIdAndSeriesIdOrderByNumber(owner.getId(),original.seriesId())).extracting(EntrySeriesMembership::getNumber).containsExactly(1);
    }
    @Test void otherSeriesAndOtherCompanyMustRemainUntouched() {
        var separate=monthly.create(manager.getId(),owner.getId(),UUID.randomUUID(),2,data(bank,category));
        remove(UUID.randomUUID(),1,0);
        for(var item:separate.items())assertThat(entries.findById(item.entryId())).isPresent();
        for(var item:foreign.items())assertThat(entries.findById(item.entryId())).isPresent();
        assertThat(series.findById(original.seriesId())).isPresent();
    }
    @Test void replayMustFreezeTheFullDeletedSetAndOldSingleReceiptsRemainCompatible() {
        var key=UUID.randomUUID();var receipt=remove(key,1,0);assertThat(receipt.deletedEntries()).hasSize(5);assertThat(remove(key,1,0)).isEqualTo(receipt);
        var single=individual.create(manager.getId(),owner.getId(),UUID.randomUUID(),data(bank,category));var singleKey=UUID.randomUUID();
        var legacy=deletion.delete(manager.getId(),owner.getId(),singleKey,single.entryId(),0L);
        jdbc.update("UPDATE financial_entry_command_receipts SET deleted_entries=NULL WHERE company_id=? AND actor_id=? AND command_id=?",owner.getId(),manager.getId(),singleKey);
        assertThat(deletion.delete(manager.getId(),owner.getId(),singleKey,single.entryId(),0L)).isEqualTo(legacy);
    }
    @Test void finalReceiptFailureMustRestoreAllMembersEntriesEventsAndKey() {
        var key=UUID.randomUUID();
        assertThatThrownBy(()->new TransactionTemplate(transactions).execute(status->{
            jdbc.execute("ALTER TABLE financial_entry_command_receipts ADD CONSTRAINT synthetic_related_delete_failure CHECK(command_id <> '"+key+"'::uuid)");return remove(key,1,0);
        })).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(count("financial_entries")).isEqualTo(5);assertThat(count("financial_entry_series_members")).isEqualTo(5);assertThat(count("financial_entry_events")).isEqualTo(5);assertThat(count("financial_entry_command_keys")).isEqualTo(1);
        assertThat(remove(key,1,0).deletedEntries()).hasSize(5);
    }
    @Test void staleManagedFutureStateMustAbortBeforeRemovingAnyMember() {
        assertThatThrownBy(()->new TransactionTemplate(transactions).execute(status->{
            assertThat(entries.findById(id(2)).orElseThrow().getVersion()).isZero();jdbc.update("UPDATE financial_entries SET version=version+1 WHERE id=?",id(2));return remove(UUID.randomUUID(),1,0);
        })).isInstanceOf(FinancialEntryCommandConflictException.class);
        assertThat(count("financial_entries")).isEqualTo(5);assertThat(count("financial_entry_series_members")).isEqualTo(5);assertThat(count("financial_entry_events")).isEqualTo(5);
    }
    @Test void competingRootsMustDeleteEachAffectedEntryAtMostOnce()throws Exception {
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            var a=pool.submit(()->concurrentRemove(start,2));var b=pool.submit(()->concurrentRemove(start,3));start.countDown();
            var results=new ArrayList<FinancialEntryCommandReceipt>();a.get(15,TimeUnit.SECONDS).ifPresent(results::add);b.get(15,TimeUnit.SECONDS).ifPresent(results::add);
            var ids=results.stream().flatMap(receipt->receipt.deletedEntries().stream()).map(FinancialEntryCommandReceipt.DeletedEntry::entryId).toList();
            assertThat(ids).containsExactlyInAnyOrder(id(2),id(3),id(4),id(5));assertThat(ids).doesNotHaveDuplicates();
            assertThat(count("financial_entries")).isEqualTo(1);assertThat(entries.findById(id(1))).isPresent();
        }finally {start.countDown();pool.shutdownNow();}
    }
    @Test void publicDeletionMustReturnTheAffectedSetAsAnAdditiveBackendResult()throws Exception {
        mvc.perform(delete("/api/companies/"+owner.getId()+"/financial-entries/"+id(1)).with(user(new UserAccountPrincipal(manager))).with(csrf()).header("Idempotency-Key",UUID.randomUUID()).queryParam("expectedVersion","0"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.entryId").value(id(1).toString())).andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.deletedEntries.length()").value(5)).andExpect(jsonPath("$.deletedEntries[0].entryId").value(id(1).toString()));
    }
    Optional<FinancialEntryCommandReceipt> concurrentRemove(CountDownLatch start,int number)throws InterruptedException {
        if(!start.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Synthetic barrier");try {return Optional.of(remove(UUID.randomUUID(),number,0));}catch(SaasResourceNotFoundException missing) {return Optional.empty();}
    }
    FinancialEntryCommandReceipt remove(UUID key,int number,long version) {return deletion.delete(manager.getId(),owner.getId(),key,id(number),version);}
    UUID id(int number) {return original.items().get(number-1).entryId();}
    void moveDue(int number,LocalDate due) {
        var row=entries.findById(id(number)).orElseThrow();var value=row.getValues();individual.update(manager.getId(),owner.getId(),UUID.randomUUID(),row.getId(),row.getVersion(),new FinancialEntryCommandData(value.direction(),value.description(),value.nominalAmount(),value.documentDate(),value.entryDate(),value.competence(),due,value.paymentDate(),value.bankAccountId(),value.categoryId(),value.partnerId(),value.resultCenterId(),value.costBehaviorOverride(),value.paymentMethod(),value.documentNumber(),value.notes()));
    }
    FinancialEntryCommandData data(UUID bank,UUID category) {var date=LocalDate.of(2026,1,1);return new FinancialEntryCommandData(FinancialDirection.EXPENSE,"Synthetic related deletion",new BigDecimal("1.23"),date,date,YearMonth.of(2026,1),LocalDate.of(2026,1,31),LocalDate.of(2026,2,2),bank,category,null,null,null,"","","");}
    long count(String table) {return jdbc.queryForObject("SELECT count(*) FROM "+table+" WHERE company_id=?",Long.class,owner.getId());}
    UUID bank(Company company) {return banks.saveAndFlush(new BankAccount(company,"Synthetic Related Bank",BankAccountType.CASH,BigDecimal.ZERO,LocalDate.of(2026,1,1),true)).getId();}
    UUID category(Company company) {var root=categories.saveAndFlush(FinancialCategory.group(company,FinancialDirection.EXPENSE,1,"Synthetic Related Group"));return categories.saveAndFlush(FinancialCategory.subcategory(root,1,"Synthetic Related Category")).getId();}
    Company company(String label,String cnpj) {var firm=firms.saveAndFlush(new AccountingFirm("Synthetic Related Firm "+label,true));firmIds.add(firm.getId());var client=clients.saveAndFlush(new Client(firm,"Synthetic Related Client "+label,true));clientIds.add(client.getId());return companies.saveAndFlush(new Company(client,"Synthetic Related Company "+label,cnpj));}
    UserAccount createUser(String label) {var actor=users.saveAndFlush(new UserAccount("Synthetic Related "+label,UUID.randomUUID()+"@example.invalid","hash"));userIds.add(actor.getId());return actor;}
    void grant(UserAccount actor,Company company) {grants.saveAndFlush(new CompanyAccessGrant(actor,company,CompanyAccessRole.CUSTOM,CostCenterScope.ALL,Set.of(CompanyPermission.FINANCIAL_ENTRY_MANAGE)));}
}
