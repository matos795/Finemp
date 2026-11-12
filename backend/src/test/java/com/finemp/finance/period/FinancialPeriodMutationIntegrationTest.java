package com.finemp.finance.period;

import com.finemp.TestcontainersConfiguration;
import com.finemp.access.*;
import com.finemp.accountingfirm.*;
import com.finemp.client.*;
import com.finemp.company.*;
import com.finemp.finance.bank.*;
import com.finemp.finance.category.*;
import com.finemp.finance.entry.*;
import com.finemp.finance.transfer.*;
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

@SpringBootTest(properties={"finemp.finance.periods.enabled=true","finemp.finance.entries.enabled=true","finemp.finance.entries.commands.enabled=true","finemp.finance.transfers.enabled=true","finemp.finance.transfers.commands.enabled=true"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class FinancialPeriodMutationIntegrationTest {
    @Autowired FinancialPeriodCommandService periods;@Autowired FinancialEntryCommandService entries;@Autowired FinancialEntryRepository rows;
    @Autowired MonthlyEntryCommandService monthly;@Autowired EntryDeletionCommandService deletion;@Autowired EntrySettlementCommandService settlement;@Autowired BankTransferCommandService transfers;
    @Autowired CompanyRepository companies;@Autowired ClientRepository clients;@Autowired AccountingFirmRepository firms;@Autowired UserAccountRepository users;@Autowired CompanyAccessGrantRepository grants;
    @Autowired BankAccountRepository banks;@Autowired FinancialCategoryRepository categories;@Autowired JdbcTemplate jdbc;@Autowired PlatformTransactionManager transactions;@Autowired MockMvc mvc;
    Company owner;Client client;AccountingFirm firm;UserAccount actor;UUID bank,destination,category;final YearMonth july=YearMonth.of(2026,7),august=july.plusMonths(1);
    @BeforeEach void prepare() {
        firm=firms.saveAndFlush(new AccountingFirm("Synthetic Period Writer Firm",true));client=clients.saveAndFlush(new Client(firm,"Synthetic Period Writer Client",true));owner=companies.saveAndFlush(new Company(client,"Synthetic Period Writer Company","97979797000197"));
        actor=users.saveAndFlush(new UserAccount("Synthetic Period Writer",UUID.randomUUID()+"@example.invalid","hash"));
        grants.saveAndFlush(new CompanyAccessGrant(actor,owner,CompanyAccessRole.CUSTOM,CostCenterScope.ALL,Set.of(CompanyPermission.FINANCIAL_ENTRY_MANAGE,CompanyPermission.BANK_ACCOUNT_MANAGE,CompanyPermission.FINANCIAL_PERIOD_CLOSE,CompanyPermission.FINANCIAL_PERIOD_REOPEN)));
        bank=bank("Origin");destination=bank("Destination");var group=categories.saveAndFlush(FinancialCategory.group(owner,FinancialDirection.EXPENSE,1,"Synthetic Writer Group"));category=categories.saveAndFlush(FinancialCategory.subcategory(group,1,"Synthetic Writer Category")).getId();
    }
    @AfterEach void cleanup() {
        for(var table:List.of("financial_entry_series_members","financial_entry_series","financial_entry_events","financial_entry_series_command_receipts","financial_entry_command_receipts","financial_entry_command_keys","financial_entries",
                "financial_bank_transfer_events","financial_bank_transfer_command_receipts","financial_bank_transfers","financial_period_command_receipts","financial_periods","financial_bank_accounts","financial_categories","access_audit_events"))jdbc.update("DELETE FROM "+table+" WHERE company_id=?",owner.getId());
        jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id IN (SELECT id FROM company_access_grants WHERE company_id=?)",owner.getId());jdbc.update("DELETE FROM company_access_grants WHERE company_id=?",owner.getId());
        companies.deleteById(owner.getId());clients.deleteById(client.getId());firms.deleteById(firm.getId());users.deleteById(actor.getId());
    }
    @Test void systemAdministratorWithoutGrantsMustRespectClosedCompetence() {
        jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id IN (SELECT id FROM company_access_grants WHERE company_id=?)", owner.getId());
        jdbc.update("DELETE FROM company_access_grants WHERE company_id=?", owner.getId());
        jdbc.update("INSERT INTO user_account_platform_permissions(user_account_id,permission) VALUES (?,'SYSTEM_ADMIN')", actor.getId());
        close(july);
        assertThatThrownBy(() -> create(july, null)).isInstanceOf(FinancialPeriodClosedException.class);
        assertThat(count("financial_entry_events")).isZero();
        reopen(july);
        create(july, null);
        assertThat(count("financial_entry_events")).isEqualTo(1);
    }

    @Test void competenceLockMustBlockCaptureAndBothSidesOfEditAsWellAsDeletionWithoutAnyJournalEffects() {
        var first=create(july,null);close(july);
        assertThatThrownBy(()->create(july,null)).isInstanceOf(FinancialPeriodClosedException.class);
        assertThatThrownBy(()->entries.update(actor.getId(),owner.getId(),UUID.randomUUID(),first.entryId(),0L,data(august,null))).isInstanceOf(FinancialPeriodClosedException.class);
        assertThatThrownBy(()->deletion.delete(actor.getId(),owner.getId(),UUID.randomUUID(),first.entryId(),0L)).isInstanceOf(FinancialPeriodClosedException.class);
        reopen(july);close(august);
        assertThatThrownBy(()->entries.update(actor.getId(),owner.getId(),UUID.randomUUID(),first.entryId(),0L,data(august,null))).isInstanceOf(FinancialPeriodClosedException.class);
        assertThat(rows.findById(first.entryId()).orElseThrow().getValues().competence()).isEqualTo(july);assertThat(count("financial_entry_events")).isEqualTo(1);assertThat(count("financial_entry_command_keys")).isEqualTo(1);
    }
    @Test void paymentMonthMustBeIndependentOfClosedCompetenceAndProtectSettlementReversalPaidCaptureAndReplay() {
        var first=create(july,null);close(july);var key=UUID.randomUUID();var paid=new EntrySettlementData(august.atDay(2),new BigDecimal("1.00"),bank);
        var receipt=settlement.settle(actor.getId(),owner.getId(),key,first.entryId(),0L,paid);close(august);
        assertThat(settlement.settle(actor.getId(),owner.getId(),key,first.entryId(),0L,paid)).isEqualTo(receipt);
        assertThatThrownBy(()->settlement.reverse(actor.getId(),owner.getId(),UUID.randomUUID(),first.entryId(),receipt.version())).isInstanceOf(FinancialPeriodClosedException.class);
        assertThatThrownBy(()->create(july.plusMonths(2),august.atDay(2))).isInstanceOf(FinancialPeriodClosedException.class);
        reopen(august);settlement.reverse(actor.getId(),owner.getId(),UUID.randomUUID(),first.entryId(),receipt.version());
        assertThat(rows.findById(first.entryId()).orElseThrow().getValues().paymentDate()).isNull();assertThat(count("financial_entry_events")).isEqualTo(3);
    }
    @Test void paidEditMustNotEscapeAClosedPreviousPaymentMonthByMovingTheDate() {
        var first=create(july,august.atDay(1));close(august);
        assertThatThrownBy(()->entries.update(actor.getId(),owner.getId(),UUID.randomUUID(),first.entryId(),0L,data(july,july.plusMonths(2).atDay(1)))).isInstanceOf(FinancialPeriodClosedException.class);
        assertThat(rows.findById(first.entryId()).orElseThrow().getValues().paymentDate()).isEqualTo(august.atDay(1));
    }
    @Test void monthlyCaptureAndRelatedDeletionMustValidateAllOccurrencesBeforeAnyEffects() {
        close(august);
        assertThatThrownBy(()->monthly.create(actor.getId(),owner.getId(),UUID.randomUUID(),3,data(july,null))).isInstanceOf(FinancialPeriodClosedException.class);
        assertThat(count("financial_entry_series")).isZero();assertThat(count("financial_entries")).isZero();assertThat(count("financial_entry_events")).isZero();
        reopen(august);var key=UUID.randomUUID();var receipt=monthly.create(actor.getId(),owner.getId(),key,3,data(july,null));close(august);
        assertThatThrownBy(()->deletion.delete(actor.getId(),owner.getId(),UUID.randomUUID(),receipt.items().getFirst().entryId(),0L)).isInstanceOf(FinancialPeriodClosedException.class);
        assertThat(count("financial_entries")).isEqualTo(3);assertThat(count("financial_entry_series_members")).isEqualTo(3);assertThat(count("financial_entry_events")).isEqualTo(3);
        assertThat(monthly.create(actor.getId(),owner.getId(),key,3,data(july,null))).isEqualTo(receipt);
    }
    @Test void transferDateLockMustProtectCreationDeletionAndOriginalReplay() {
        var values=new BankTransferValues(bank,destination,july.atDay(3),new BigDecimal("1"),"Synthetic transfer");var key=UUID.randomUUID();var receipt=transfers.create(actor.getId(),owner.getId(),key,values);close(july);
        assertThat(transfers.create(actor.getId(),owner.getId(),key,values)).isEqualTo(receipt);
        assertThatThrownBy(()->transfers.create(actor.getId(),owner.getId(),UUID.randomUUID(),values)).isInstanceOf(FinancialPeriodClosedException.class);
        assertThatThrownBy(()->transfers.delete(actor.getId(),owner.getId(),UUID.randomUUID(),receipt.transferId(),0L)).isInstanceOf(FinancialPeriodClosedException.class);
        assertThat(count("financial_bank_transfers")).isEqualTo(1);assertThat(count("financial_bank_transfer_events")).isEqualTo(1);
    }
    @Test void writerWaitingBehindClosingTransactionMustRecheckTheCommittedLock()throws Exception {
        var pool=Executors.newSingleThreadExecutor();var entered=new CountDownLatch(1);var waiting=new java.util.concurrent.atomic.AtomicReference<Future<FinancialEntryCommandReceipt>>();
        try {
            new TransactionTemplate(transactions).executeWithoutResult(status->{
                close(july);waiting.set(pool.submit(()->{entered.countDown();return create(july,null);}));
                try {assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();assertThatThrownBy(()->waiting.get().get(100,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);}
                catch(InterruptedException interrupted) {Thread.currentThread().interrupt();throw new IllegalStateException(interrupted);}
            });
            assertThatThrownBy(()->waiting.get().get(10,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class).hasCauseInstanceOf(FinancialPeriodClosedException.class);
            assertThat(count("financial_entries")).isZero();assertThat(count("financial_entry_events")).isZero();assertThat(count("financial_entry_command_keys")).isZero();
        }finally {pool.shutdownNow();}
    }
    @Test void existingDeletionAndTransferRoutesMustReturnSafePeriodConflict()throws Exception {
        var first=create(july,null);var transfer=transfers.create(actor.getId(),owner.getId(),UUID.randomUUID(),new BankTransferValues(bank,destination,july.atDay(3),new BigDecimal("1"),"Synthetic transfer"));close(july);
        for(var suffix:List.of("financial-entries/"+first.entryId(),"bank-transfers/"+transfer.transferId()))
            mvc.perform(delete("/api/companies/"+owner.getId()+"/"+suffix).queryParam("expectedVersion","0").header("Idempotency-Key",UUID.randomUUID()).with(user(new UserAccountPrincipal(actor))).with(csrf()))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("FINANCIAL_PERIOD_CLOSED"));
        assertThat(count("financial_entries")).isEqualTo(1);assertThat(count("financial_bank_transfers")).isEqualTo(1);
    }
    FinancialEntryCommandReceipt create(YearMonth competence,LocalDate paid) {return entries.create(actor.getId(),owner.getId(),UUID.randomUUID(),data(competence,paid));}
    FinancialEntryCommandData data(YearMonth competence,LocalDate paid) {var date=july.atDay(1);return new FinancialEntryCommandData(FinancialDirection.EXPENSE,"Synthetic locked entry",new BigDecimal("1"),date,date,competence,date,paid,bank,category,null,null,null,"","","");}
    void close(YearMonth period) {var version=jdbc.query("SELECT version FROM financial_periods WHERE company_id=? AND period=?",(row,index)->row.getLong("version"),owner.getId(),period.toString()).stream().findFirst().orElse(null);periods.close(actor.getId(),owner.getId(),UUID.randomUUID(),period,version);}
    void reopen(YearMonth period) {periods.reopen(actor.getId(),owner.getId(),UUID.randomUUID(),period,0L,"Ajuste autorizado para estudo");}
    long count(String table) {return jdbc.queryForObject("SELECT count(*) FROM "+table+" WHERE company_id=?",Long.class,owner.getId());}
    UUID bank(String name) {return banks.saveAndFlush(new BankAccount(owner,"Synthetic Period "+name,BankAccountType.CASH,BigDecimal.ZERO,july.atDay(1),true)).getId();}
}
