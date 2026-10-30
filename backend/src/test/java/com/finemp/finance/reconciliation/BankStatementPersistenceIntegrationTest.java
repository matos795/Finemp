package com.finemp.finance.reconciliation;

import com.finemp.TestcontainersConfiguration;
import com.finemp.access.*;
import com.finemp.administration.*;
import com.finemp.accountingfirm.*;
import com.finemp.client.*;
import com.finemp.company.*;
import com.finemp.finance.bank.*;
import com.finemp.finance.category.*;
import com.finemp.finance.entry.*;
import com.finemp.finance.partner.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.*;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={"finemp.finance.catalog.enabled=true","finemp.finance.statement-import.enabled=true","finemp.finance.statement-candidates.enabled=true","finemp.finance.statement-read.enabled=true","finemp.finance.aging.enabled=true","finemp.finance.open-titles.enabled=true","finemp.finance.monthly-cash.enabled=true","finemp.finance.daily-cash.enabled=true","finemp.finance.statement-actions.enabled=true","finemp.finance.entries.enabled=true","finemp.finance.entries.commands.enabled=true"})
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@org.springframework.test.context.TestPropertySource(properties="finemp.finance.presentation.enabled=true")
class BankStatementPersistenceIntegrationTest {
    @Autowired BusinessPartnerRepository partners;
    @Autowired com.finemp.finance.reporting.FinancialPresentationSnapshotService presentation;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean(name="dailyCashTotalsService")
    com.finemp.finance.reporting.DailyCashTotalsService dailySnapshotProbe;
    @Autowired EntrySettlementCommandService settlements;
    @Autowired EntryDeletionCommandService deletions;
    @Autowired FinancialEntryCommandService entryCommands;
    @Autowired EntryWriteTransactionGuard entryGuard;
    @Autowired BankStatementItemRepository statements;@Autowired BankAccountRepository banks;@Autowired BankAccountService bankService;
    @Autowired FinancialEntryRepository entries;@Autowired FinancialCategoryRepository categories;@Autowired CompanyRepository companies;@Autowired ClientRepository clients;@Autowired AccountingFirmRepository firms;@Autowired UserAccountRepository users;@Autowired CompanyAccessGrantRepository grants;
    @Autowired @org.springframework.beans.factory.annotation.Qualifier("financialStatementBankReferences") BankAccountReferenceGuard guard;
    @Autowired JdbcTemplate jdbc;@Autowired PlatformTransactionManager transactions;@Autowired StatementImportCommandService imports;@Autowired StatementCandidateReadService candidates;@Autowired StatementItemReadService statementReads;@Autowired StatementIgnoreCommandService ignores;@Autowired com.finemp.finance.reporting.OpenTitlesAgingService aging;@Autowired com.finemp.finance.reporting.OpenTitlesReadService openTitles;@Autowired com.finemp.finance.reporting.MonthlyCashTotalsService monthly;@Autowired com.finemp.finance.reporting.DailyCashTotalsService daily;@Autowired org.springframework.test.web.servlet.MockMvc mvc;
    Company owner,other;BankAccount bank,second,foreignBank;FinancialEntry entry,foreignEntry;UserAccount actor;final List<UUID> firmIds=new ArrayList<>(),clientIds=new ArrayList<>();final Map<UUID,UUID> categoryIds=new HashMap<>();
    @BeforeEach void prepare(){
        owner=company("Owner","70707070000170");other=company("Other","80808080000180");actor=users.saveAndFlush(new UserAccount("Synthetic Statement Actor",UUID.randomUUID()+"@example.invalid","hash"));
        grants.saveAndFlush(new CompanyAccessGrant(actor,owner,CompanyAccessRole.CUSTOM,CostCenterScope.ALL,Set.of(CompanyPermission.BANK_ACCOUNT_MANAGE,CompanyPermission.FINANCIAL_ENTRY_MANAGE)));
        bank=bank(owner,"Primary");second=bank(owner,"Secondary");foreignBank=bank(other,"Foreign");entry=entry(owner,bank);foreignEntry=entry(other,foreignBank);
    }
    @AfterEach void cleanup(){
        for(var company:List.of(owner,other)){
            for(var table:List.of("financial_statement_action_receipts","financial_statement_import_receipts","financial_bank_statement_items","financial_entries","business_partners","financial_periods","financial_bank_accounts","financial_categories","access_audit_events"))jdbc.update("DELETE FROM "+table+" WHERE company_id=?",company.getId());
            jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id IN (SELECT id FROM company_access_grants WHERE company_id=?)",company.getId());jdbc.update("DELETE FROM company_access_grants WHERE company_id=?",company.getId());companies.deleteById(company.getId());
        }
        for(var id:clientIds)clients.deleteById(id);for(var id:firmIds)firms.deleteById(id);users.deleteById(actor.getId());
    }
    @Test void exactSignedValuesAndScopeMustPersistWithoutChangingBankOrEntry(){
        var row=save(owner,bank,value("-12.34","Tarifa","FIT-1"));var loaded=statements.findByIdAndCompanyId(row.getId(),owner.getId()).orElseThrow();
        assertThat(loaded.getValues().signedAmount()).isEqualTo(new BigDecimal("-12.34"));assertThat(loaded.getStatus()).isEqualTo(StatementStatus.PENDING);assertThat(loaded.getLinkedEntryId()).isNull();assertThat(loaded.getVersion()).isZero();
        assertThat(statements.findByIdAndCompanyId(row.getId(),other.getId())).isEmpty();assertThat(statements.findByCompanyIdAndBankAccountId(other.getId(),bank.getId(),PageRequest.of(0,10))).isEmpty();
        assertThat(entries.findById(entry.getId()).orElseThrow().getValues().nominalAmount()).isEqualByComparingTo("1");assertThat(banks.findById(bank.getId()).orElseThrow().getOpeningBalance()).isEqualByComparingTo("0");
    }
    @Test void identityMustDeduplicateWithinTheSameAccountButNotOtherAccountsOrCompanies(){
        var initial=save(owner,bank,value("1","First","FIT-1"));
        assertThat(statements.existsByCompanyIdAndBankAccountIdAndIdentityKey(owner.getId(),bank.getId(),initial.getIdentityKey())).isTrue();
        assertThatThrownBy(()->save(owner,bank,value("2","Different","FIT-1"))).isInstanceOf(DataIntegrityViolationException.class);
        save(owner,second,value("2","Different","FIT-1"));save(other,foreignBank,value("2","Different","FIT-1"));
        var firstFallback=save(owner,bank,value("-1.20","TÁXÍ", ""));var secondFallback=save(owner,bank,value("-1.2"," taxi ", ""));
        assertThat(firstFallback.getIdentityKey()).isEqualTo(secondFallback.getIdentityKey());assertThat(firstFallback.getId()).isNotEqualTo(secondFallback.getId());
        assertThat(statements.count()).isEqualTo(5);
    }
    @Test void physicalReferencesAndUniqueLinkMustRejectCrossCompanyAndDuplicateReconciliation(){
        var first=save(owner,bank,value("1","First","F1"));var secondRow=save(owner,bank,value("2","Second","F2"));
        assertThatThrownBy(()->new BankStatementItem(owner,foreignBank,value("3","Foreign","F3"),actor.getId())).isInstanceOf(IllegalArgumentException.class);
        fails("UPDATE financial_bank_statement_items SET bank_account_id=? WHERE id=?",foreignBank.getId(),first.getId());
        fails("UPDATE financial_bank_statement_items SET status='RECONCILED',linked_entry_id=? WHERE id=?",foreignEntry.getId(),first.getId());
        jdbc.update("UPDATE financial_bank_statement_items SET status='RECONCILED',linked_entry_id=? WHERE id=?",entry.getId(),first.getId());
        fails("UPDATE financial_bank_statement_items SET status='RECONCILED',linked_entry_id=? WHERE id=?",entry.getId(),secondRow.getId());
        assertThat(statements.findById(secondRow.getId()).orElseThrow().getStatus()).isEqualTo(StatementStatus.PENDING);
    }
    @Test void bankReferenceMustRemainProtectedWithoutAStatementFeatureFlag(){
        var row=save(owner,bank,value("1","First","F1"));assertThat(guard.hasReferences(owner.getId(),bank.getId())).isTrue();assertThat(guard.hasReferences(other.getId(),bank.getId())).isFalse();
        assertThat(bankService.delete(actor.getId(),owner.getId(),bank.getId(),0L).action()).isEqualTo(BankAccountService.DeleteAction.DEACTIVATED);assertThat(statements.findById(row.getId())).isPresent();
    }
    @Test void databaseMustRejectInconsistentStateBadAmountAndStaleDeletion(){
        var row=save(owner,bank,value("1","First","F1"));
        for(var clause:List.of("status='RECONCILED'","linked_entry_id='"+entry.getId()+"'::uuid","signed_amount=0","signed_amount='NaN'","version=-1","identity_key='invalid'"))
            fails("UPDATE financial_bank_statement_items SET "+clause+" WHERE id=?",row.getId());
        jdbc.update("UPDATE financial_bank_statement_items SET version=version+1 WHERE id=?",row.getId());assertThatThrownBy(()->statements.delete(row)).isInstanceOf(OptimisticLockingFailureException.class);assertThat(statements.findById(row.getId())).isPresent();
    }
    @Test void concurrentIdenticalIdentityMustPersistExactlyOneItem()throws Exception{
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try{Callable<Boolean> task=()->{if(!start.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Barrier");try{save(owner,bank,value("1","First","F1"));return true;}catch(DataIntegrityViolationException expected){return false;}};var a=pool.submit(task);var b=pool.submit(task);start.countDown();assertThat(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);assertThat(statements.count()).isEqualTo(1);}finally{start.countDown();pool.shutdownNow();}
    }
    @Test void concurrentSimilarMovementsWithoutFitidMustBothRemainAvailable()throws Exception{
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try{Callable<UUID> task=()->{if(!start.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Barrier");return save(owner,bank,value("1","Synthetic recurring debit","")).getId();};var a=pool.submit(task);var b=pool.submit(task);start.countDown();assertThat(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS))).doesNotHaveDuplicates();assertThat(statements.count()).isEqualTo(2);}finally{start.countDown();pool.shutdownNow();}
    }
    @Test void importReceiptMustRemainScopedUniqueAndProtectTheBank(){
        var command=UUID.randomUUID();receipt(owner,bank,command);
        assertThat(guard.hasReferences(owner.getId(),bank.getId())).isTrue();assertThat(guard.hasReferences(other.getId(),bank.getId())).isFalse();
        assertThatThrownBy(()->receipt(owner,bank,command)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(()->receipt(owner,foreignBank,UUID.randomUUID())).isInstanceOf(DataIntegrityViolationException.class);
        receipt(other,foreignBank,command);
        assertThat(bankService.delete(actor.getId(),owner.getId(),bank.getId(),0L).action()).isEqualTo(BankAccountService.DeleteAction.DEACTIVATED);
    }
    @Test void importReceiptAndMovementsRollBackTogether(){
        var command=UUID.randomUUID();
        assertThatThrownBy(()->new TransactionTemplate(transactions).execute(status->{receipt(owner,bank,command);save(owner,bank,value("1","Synthetic rollback","rollback"));throw new IllegalStateException("synthetic rollback");})).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM financial_statement_import_receipts WHERE company_id=?",Long.class,owner.getId())).isZero();assertThat(statements.count()).isZero();
    }
    @Test void concurrentSameImportReceiptHasExactlyOneWinner()throws Exception{
        var command=UUID.randomUUID();var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try{Callable<Boolean> task=()->{if(!start.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Barrier");try{receipt(owner,bank,command);return true;}catch(DataIntegrityViolationException expected){return false;}};var a=pool.submit(task);var b=pool.submit(task);start.countDown();assertThat(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);}finally{start.countDown();pool.shutdownNow();}
    }
    @Test void importCommandIsIdempotentScopedAndPreservesSimilarRows(){
        var command=UUID.randomUUID();var items=List.of(value("1.20","Synthetic recurring",""),value("1.20","Synthetic recurring",""));
        var first=imports.confirm(actor.getId(),owner.getId(),command,bank.getId(),items);var replay=imports.confirm(actor.getId(),owner.getId(),command,bank.getId(),items);
        assertThat(replay).isEqualTo(first);assertThat(first.itemIds()).hasSize(2).doesNotHaveDuplicates();assertThat(statements.count()).isEqualTo(2);
        assertThatThrownBy(()->imports.confirm(actor.getId(),owner.getId(),command,bank.getId(),List.of(value("2","Different","")))).isInstanceOf(StatementImportConflictException.class).extracting(e->((StatementImportConflictException)e).getReason()).isEqualTo(StatementImportConflictException.Reason.IDEMPOTENCY_MISMATCH);
        assertThatThrownBy(()->imports.confirm(actor.getId(),other.getId(),UUID.randomUUID(),foreignBank.getId(),items)).isInstanceOf(OrganizationAccessDeniedException.class);
        assertThatThrownBy(()->imports.confirm(actor.getId(),owner.getId(),UUID.randomUUID(),foreignBank.getId(),items)).isInstanceOf(SaasResourceNotFoundException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM access_audit_events WHERE company_id=? AND action='FINANCIAL_STATEMENT_IMPORTED'",Long.class,owner.getId())).isEqualTo(1);
        var provenanceCommand=UUID.randomUUID();imports.confirmWithProvenance(actor.getId(),owner.getId(),provenanceCommand,bank.getId(),items,"a".repeat(64));
        assertThatThrownBy(()->imports.confirmWithProvenance(actor.getId(),owner.getId(),provenanceCommand,bank.getId(),items,"b".repeat(64))).isInstanceOf(StatementImportConflictException.class).extracting(e->((StatementImportConflictException)e).getReason()).isEqualTo(StatementImportConflictException.Reason.IDEMPOTENCY_MISMATCH);
    }
    @Test void closedPeriodAndDuplicateFitidDoNotWritePartialImport(){
        var command=UUID.randomUUID();var items=List.of(value("1","One","same"),value("2","Two","same"));
        assertThatThrownBy(()->imports.confirm(actor.getId(),owner.getId(),command,bank.getId(),items)).isInstanceOf(StatementImportConflictException.class).extracting(e->((StatementImportConflictException)e).getReason()).isEqualTo(StatementImportConflictException.Reason.DUPLICATE_FITID);
        jdbc.update("INSERT INTO financial_periods(id,company_id,period,closed,version,updated_by,updated_at) VALUES(?,?,'2026-07',true,0,?,now())",UUID.randomUUID(),owner.getId(),actor.getId());
        assertThatThrownBy(()->imports.confirm(actor.getId(),owner.getId(),command,bank.getId(),List.of(value("1","One","")))).isInstanceOf(com.finemp.finance.period.FinancialPeriodClosedException.class);
        assertThat(statements.count()).isZero();assertThat(jdbc.queryForObject("SELECT count(*) FROM financial_statement_import_receipts WHERE company_id=?",Long.class,owner.getId())).isZero();
    }
    @Test void concurrentSameImportCommandReturnsOneReceiptWithoutDuplicateMovements()throws Exception{
        var command=UUID.randomUUID();var items=List.of(value("1","Synthetic concurrent",""));var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try{Callable<StatementImportCommandService.Receipt> task=()->{if(!start.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Barrier");return imports.confirm(actor.getId(),owner.getId(),command,bank.getId(),items);};var a=pool.submit(task);var b=pool.submit(task);start.countDown();assertThat(a.get(15,TimeUnit.SECONDS)).isEqualTo(b.get(15,TimeUnit.SECONDS));assertThat(statements.count()).isEqualTo(1);}finally{start.countDown();pool.shutdownNow();}
    }
    @Test void existingFitidAbortsAllNewRowsAndCommittedReplaySurvivesClosure(){
        save(owner,bank,value("1","Existing","fit-existing"));
        assertThatThrownBy(()->imports.confirm(actor.getId(),owner.getId(),UUID.randomUUID(),bank.getId(),List.of(value("2","New",""),value("3","Duplicate","fit-existing")))).isInstanceOf(StatementImportConflictException.class);
        assertThat(statements.count()).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT count(*) FROM financial_statement_import_receipts WHERE company_id=?",Long.class,owner.getId())).isZero();
        var command=UUID.randomUUID();var items=List.of(value("5","Synthetic posted","fit-new"));var result=imports.confirm(actor.getId(),owner.getId(),command,bank.getId(),items);
        jdbc.update("INSERT INTO financial_periods(id,company_id,period,closed,version,updated_by,updated_at) VALUES(?,?,'2026-07',true,0,?,now())",UUID.randomUUID(),owner.getId(),actor.getId());
        assertThat(imports.confirm(actor.getId(),owner.getId(),command,bank.getId(),items)).isEqualTo(result);assertThat(statements.count()).isEqualTo(2);
    }
    @Test void candidatesAreScopedPaginatedAndExcludeAlreadyLinkedEntries()throws Exception{
        jdbc.update("UPDATE financial_entries SET nominal_amount=9 WHERE id=?",entry.getId());
        var statement=save(owner,bank,value("-1.00","Synthetic item","target"));var paid=entry(owner,bank);var linked=entry(owner,bank);var open=entry(owner,second);
        jdbc.update("UPDATE financial_entries SET payment_date=DATE '2026-07-01' WHERE id IN (?,?)",paid.getId(),linked.getId());
        var linkedStatement=save(owner,bank,value("-1.00","Already linked","linked"));jdbc.update("UPDATE financial_bank_statement_items SET status='RECONCILED',linked_entry_id=? WHERE id=?",linked.getId(),linkedStatement.getId());
        var first=candidates.list(actor.getId(),owner.getId(),statement.getId(),null,0,1);var next=candidates.list(actor.getId(),owner.getId(),statement.getId(),null,1,1);
        assertThat(first.totalItems()).isEqualTo(2);assertThat(first.items().getFirst().entryId()).isEqualTo(paid.getId());assertThat(next.items().getFirst().entryId()).isEqualTo(open.getId());
        assertThat(candidates.list(actor.getId(),owner.getId(),statement.getId(),StatementCandidateReadService.Status.OPEN,0,20).totalItems()).isEqualTo(1);
        assertThatThrownBy(()->candidates.list(actor.getId(),other.getId(),statement.getId(),null,0,20)).isInstanceOf(OrganizationAccessDeniedException.class);
        var route="/api/companies/"+owner.getId()+"/statement-items/"+statement.getId()+"/candidates";
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(route).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(new com.finemp.security.UserAccountPrincipal(actor))).param("size","1"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.items[0].nominalAmount").value("1.00"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(route.replace(owner.getId().toString(),other.getId().toString())).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(new com.finemp.security.UserAccountPrincipal(actor))))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
    }
    @Test void candidateDateAndMoneyBoundariesMatchTheVerifiedMatcher(){
        jdbc.update("UPDATE financial_entries SET nominal_amount=9 WHERE id=?",entry.getId());var statement=save(owner,bank,value("-1.00","Synthetic boundaries","boundaries"));
        var paidWithin=entry(owner,bank);var paidOutside=entry(owner,bank);var amountOutside=entry(owner,bank);var openWithin=entry(owner,second);var openOutside=entry(owner,second);
        jdbc.update("UPDATE financial_entries SET payment_date=DATE '2026-07-04',actual_paid_amount=1.01 WHERE id=?",paidWithin.getId());
        jdbc.update("UPDATE financial_entries SET payment_date=DATE '2026-07-05' WHERE id=?",paidOutside.getId());
        jdbc.update("UPDATE financial_entries SET nominal_amount=1.02 WHERE id=?",amountOutside.getId());
        jdbc.update("UPDATE financial_entries SET due_date=DATE '2026-07-16' WHERE id=?",openWithin.getId());
        jdbc.update("UPDATE financial_entries SET due_date=DATE '2026-07-17' WHERE id=?",openOutside.getId());
        var rows=candidates.list(actor.getId(),owner.getId(),statement.getId(),null,0,20).items();
        assertThat(rows).extracting(StatementCandidateReadService.View::entryId).containsExactly(paidWithin.getId(),openWithin.getId());
        assertThat(rows.getFirst().actualPaidAmount()).isEqualByComparingTo("1.01");assertThat(rows.getFirst().distanceDays()).isEqualTo(3);
    }
    @Test void openTitleAgingUsesExplicitDateNominalAmountAndEightBoundaryBands()throws Exception{
        LocalDate base=LocalDate.of(2026,7,1);int[] days={0,-31,-61,-91,1,31,61,91};var created=new ArrayList<UUID>();
        for(int daysLate:days){var e=entry(owner,bank);created.add(e.getId());jdbc.update("UPDATE financial_entries SET due_date=?,nominal_amount=1.01 WHERE id=?",base.minusDays(daysLate),e.getId());}
        jdbc.update("UPDATE financial_entries SET payment_date=?,actual_paid_amount=9 WHERE id=?",base.plusDays(1),created.getFirst());
        jdbc.update("UPDATE financial_entries SET competence='2026-08' WHERE id=?",created.getLast());
        var closed=entry(owner,bank);jdbc.update("UPDATE financial_entries SET payment_date=? WHERE id=?",base,closed.getId());
        var result=aging.report(actor.getId(),owner.getId(),FinancialDirection.EXPENSE,base,com.finemp.finance.reporting.OpenTitlesAgingService.Scope.ALL);
        assertThat(result.totalCount()).isEqualTo(9);assertThat(result.totalAmount()).isEqualByComparingTo("9.08");assertThat(result.bands()).hasSize(8);
        assertThat(result.overdueCount()).isEqualTo(4);assertThat(result.overdueAmount()).isEqualByComparingTo("4.04");assertThat(result.dueWithin30Count()).isEqualTo(2);assertThat(result.dueWithin30Amount()).isEqualByComparingTo("2.01");
        var empty=aging.report(actor.getId(),owner.getId(),FinancialDirection.INCOME,base,com.finemp.finance.reporting.OpenTitlesAgingService.Scope.ALL);assertThat(empty.overdueCount()).isZero();assertThat(empty.overdueAmount()).isEqualByComparingTo("0.00");assertThat(empty.dueWithin30Amount()).isEqualByComparingTo("0.00");
        assertThat(result.bands()).allSatisfy(band->assertThat(band.count()).isGreaterThanOrEqualTo(1));
        for(var band:result.bands()){
            var detail=openTitles.list(actor.getId(),owner.getId(),FinancialDirection.EXPENSE,base,com.finemp.finance.reporting.OpenTitlesAgingService.Scope.ALL,band.band(),0,100);
            assertThat(detail.totalItems()).isEqualTo(band.count());assertThat(detail.items()).allSatisfy(row->assertThat(row.band()).isEqualTo(band.band()));
            assertThat(detail.items().stream().map(com.finemp.finance.reporting.OpenTitlesReadService.View::nominalAmount).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo(band.amount());
        }
        var restricted=aging.report(actor.getId(),owner.getId(),FinancialDirection.EXPENSE,base,com.finemp.finance.reporting.OpenTitlesAgingService.Scope.COMPETENCE);
        assertThat(restricted.totalCount()).isEqualTo(8);assertThat(restricted.totalAmount()).isEqualByComparingTo("8.07");
        assertThatThrownBy(()->aging.report(actor.getId(),other.getId(),FinancialDirection.EXPENSE,base,com.finemp.finance.reporting.OpenTitlesAgingService.Scope.ALL)).isInstanceOf(OrganizationAccessDeniedException.class);
        var route="/api/companies/"+owner.getId()+"/financial-reports/open-titles/aging";
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(route).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(new com.finemp.security.UserAccountPrincipal(actor))).param("direction","EXPENSE").param("asOf","2026-07-01"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.totalAmount").value("9.08"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(route).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(new com.finemp.security.UserAccountPrincipal(actor))).param("direction","EXPENSE"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
    }
    @Test void ignoreIsIdempotentAuditedScopedAndCannotBypassClosedPeriod()throws Exception{
        var statement=save(owner,bank,value("-1","Synthetic ignore","ignore"));var command=UUID.randomUUID();var result=ignores.ignore(actor.getId(),owner.getId(),command,statement.getId(),0L);
        assertThat(ignores.ignore(actor.getId(),owner.getId(),command,statement.getId(),0L)).isEqualTo(result);assertThat(statements.findById(statement.getId()).orElseThrow().getStatus()).isEqualTo(StatementStatus.IGNORED);
        assertThatThrownBy(()->ignores.ignore(actor.getId(),owner.getId(),command,statement.getId(),1L)).isInstanceOf(StatementActionConflictException.class);
        assertThatThrownBy(()->ignores.ignore(actor.getId(),other.getId(),UUID.randomUUID(),statement.getId(),0L)).isInstanceOf(OrganizationAccessDeniedException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM access_audit_events WHERE company_id=? AND action='FINANCIAL_STATEMENT_IGNORED'",Long.class,owner.getId())).isEqualTo(1);
        var pending=save(owner,bank,value("-2","Synthetic closed","closed"));jdbc.update("INSERT INTO financial_periods(id,company_id,period,closed,version,updated_by,updated_at) VALUES(?,?,'2026-07',true,0,?,now())",UUID.randomUUID(),owner.getId(),actor.getId());
        assertThatThrownBy(()->ignores.ignore(actor.getId(),owner.getId(),UUID.randomUUID(),pending.getId(),0L)).isInstanceOf(com.finemp.finance.period.FinancialPeriodClosedException.class);
        assertThat(ignores.ignore(actor.getId(),owner.getId(),command,statement.getId(),0L)).isEqualTo(result);assertThat(statements.findById(pending.getId()).orElseThrow().getStatus()).isEqualTo(StatementStatus.PENDING);
    }
    @Test void ignoreRejectsAlreadyReconciledAndForeignItems(){
        var statement=save(owner,bank,value("-1","Synthetic linked","linked-ignore"));jdbc.update("UPDATE financial_bank_statement_items SET status='RECONCILED',linked_entry_id=? WHERE id=?",entry.getId(),statement.getId());
        assertThatThrownBy(()->ignores.ignore(actor.getId(),owner.getId(),UUID.randomUUID(),statement.getId(),0L)).isInstanceOf(StatementActionConflictException.class);
        var foreign=save(other,foreignBank,value("-1","Synthetic foreign","foreign-ignore"));assertThatThrownBy(()->ignores.ignore(actor.getId(),owner.getId(),UUID.randomUUID(),foreign.getId(),0L)).isInstanceOf(SaasResourceNotFoundException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM financial_statement_action_receipts WHERE company_id=?",Long.class,owner.getId())).isZero();
    }
    @Test void ignoreRollsBackWithOuterTransactionAndConcurrentRetriesReturnOneResult()throws Exception{
        var statement=save(owner,bank,value("-1","Synthetic ignore","ignore"));var command=UUID.randomUUID();
        assertThatThrownBy(()->new TransactionTemplate(transactions).execute(status->{ignores.ignore(actor.getId(),owner.getId(),command,statement.getId(),0L);throw new IllegalStateException("synthetic rollback");})).isInstanceOf(IllegalStateException.class);
        assertThat(statements.findById(statement.getId()).orElseThrow().getStatus()).isEqualTo(StatementStatus.PENDING);
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try{Callable<StatementIgnoreCommandService.Receipt> task=()->{if(!start.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Barrier");return ignores.ignore(actor.getId(),owner.getId(),command,statement.getId(),0L);};var a=pool.submit(task);var b=pool.submit(task);start.countDown();assertThat(a.get(15,TimeUnit.SECONDS)).isEqualTo(b.get(15,TimeUnit.SECONDS));}finally{start.countDown();pool.shutdownNow();}
        assertThat(jdbc.queryForObject("SELECT count(*) FROM financial_statement_action_receipts WHERE company_id=?",Long.class,owner.getId())).isEqualTo(1);
    }
    @Test void ignoreHttpRequiresCsrfAndStrictBody()throws Exception{
        var statement=save(owner,bank,value("-1","Synthetic http","http"));var route="/api/companies/"+owner.getId()+"/statement-items/"+statement.getId()+"/ignore";
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(route).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(new com.finemp.security.UserAccountPrincipal(actor))).header("Idempotency-Key",UUID.randomUUID()).contentType("application/json").content("{\"expectedVersion\":0}"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(route).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(new com.finemp.security.UserAccountPrincipal(actor))).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).header("Idempotency-Key",UUID.randomUUID()).contentType("application/json").content("{\"expectedVersion\":0,\"extra\":1}"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(route).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(new com.finemp.security.UserAccountPrincipal(actor))).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).header("Idempotency-Key",UUID.randomUUID()).contentType("application/json").content("{\"expectedVersion\":0}"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.version").value(1));
    }
    @Test void monthlyCashTotalsUsePaymentDateAndEffectiveValueOrOpenDueDate()throws Exception{
        var paid=entry(owner,bank);jdbc.update("UPDATE financial_entries SET nominal_amount=100,payment_date=DATE '2026-08-05',actual_paid_amount=75.30 WHERE id=?",paid.getId());
        var group=categories.saveAndFlush(FinancialCategory.group(owner,FinancialDirection.INCOME,1,"Synthetic Income Group"));var sub=categories.saveAndFlush(FinancialCategory.subcategory(group,1,"Synthetic Income Category"));
        var d=LocalDate.of(2026,7,1);entries.saveAndFlush(new FinancialEntry(owner,new FinancialEntryValues(FinancialDirection.INCOME,"Synthetic monthly income",new BigDecimal("10.10"),d,d,YearMonth.of(2026,7),d,null,null,bank.getId(),sub.getId(),null,null,null,"","",""),actor.getId()));
        var result=monthly.report(actor.getId(),owner.getId(),2026);assertThat(result.months()).hasSize(12);
        assertThat(result.months().get(6).openIncome()).isEqualByComparingTo("10.10");assertThat(result.months().get(6).openExpense()).isEqualByComparingTo("1.00");
        assertThat(result.months().get(7).realExpense()).isEqualByComparingTo("75.30");assertThat(result.months().get(0).realIncome()).isEqualByComparingTo("0.00");
        assertThat(monthly.report(actor.getId(),owner.getId(),9999).months()).hasSize(12);
        assertThatThrownBy(()->monthly.report(actor.getId(),other.getId(),2026)).isInstanceOf(OrganizationAccessDeniedException.class);
        var route="/api/companies/"+owner.getId()+"/financial-reports/monthly-cash-totals";
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(route).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(new com.finemp.security.UserAccountPrincipal(actor))).param("year","2026"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.months[7].realExpense").value("75.30"));
    }
    @Test void monthlyCashTotalsKeepExactSumsBeyondIndividualMoneyPrecision(){
        var otherEntry=entry(owner,bank);jdbc.update("UPDATE financial_entries SET nominal_amount=99999999999999999.99 WHERE id IN (?,?)",entry.getId(),otherEntry.getId());
        var july=monthly.report(actor.getId(),owner.getId(),2026).months().get(6);assertThat(july.openExpense()).isEqualByComparingTo("199999999999999999.98");
    }
    @Test void dailyCashTotalsUseRealPaymentAndOpenDueDatesIncludingLeapDay()throws Exception{
        var paid=entry(owner,bank);var open=entry(owner,bank);jdbc.update("UPDATE financial_entries SET payment_date=DATE '2024-02-01',actual_paid_amount=1.23 WHERE id=?",paid.getId());jdbc.update("UPDATE financial_entries SET due_date=DATE '2024-02-29' WHERE id=?",open.getId());
        var result=daily.report(actor.getId(),owner.getId(),"2024-02");assertThat(result.days()).hasSize(29);assertThat(result.days().getFirst().realExpense()).isEqualByComparingTo("1.23");assertThat(result.days().getLast().openExpense()).isEqualByComparingTo("1.00");
        assertThat(daily.report(actor.getId(),owner.getId(),"9999-12").days()).hasSize(31);
        assertThatThrownBy(()->daily.report(actor.getId(),other.getId(),"2024-02")).isInstanceOf(OrganizationAccessDeniedException.class);
        var route="/api/companies/"+owner.getId()+"/financial-reports/daily-cash-totals";
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(route).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(new com.finemp.security.UserAccountPrincipal(actor))).param("month","2024-02"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.days[0].realExpense").value("1.23"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(route).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(new com.finemp.security.UserAccountPrincipal(actor))).param("month","2024-13"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
    }
    @Test void statementItemReadFiltersAndPaginatesWithinCompanyAndAccount()throws Exception{
        var a=save(owner,bank,value("-1.20","Synthetic first","r1"));var b=save(owner,bank,value("2.50","Synthetic ignored","r2"));save(other,foreignBank,value("9","Synthetic foreign","foreign-r"));
        jdbc.update("UPDATE financial_bank_statement_items SET status='IGNORED',posted_date=DATE '2026-08-01' WHERE id=?",b.getId());
        var all=statementReads.list(actor.getId(),owner.getId(),bank.getId(),null,null,null,0,1);assertThat(all.totalItems()).isEqualTo(2);assertThat(all.items().getFirst().id()).isEqualTo(b.getId());
        var pending=statementReads.list(actor.getId(),owner.getId(),bank.getId(),StatementStatus.PENDING,LocalDate.of(2026,7,1),LocalDate.of(2026,7,31),0,20);assertThat(pending.totalItems()).isEqualTo(1);assertThat(pending.items().getFirst().id()).isEqualTo(a.getId());assertThat(pending.items().getFirst().values().signedAmount()).isEqualByComparingTo("-1.20");
        assertThatThrownBy(()->statementReads.list(actor.getId(),other.getId(),foreignBank.getId(),null,null,null,0,20)).isInstanceOf(OrganizationAccessDeniedException.class);
        assertThatThrownBy(()->statementReads.list(actor.getId(),owner.getId(),foreignBank.getId(),null,null,null,0,20)).isInstanceOf(SaasResourceNotFoundException.class);
        var route="/api/companies/"+owner.getId()+"/bank-accounts/"+bank.getId()+"/statement-items";
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(route).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(new com.finemp.security.UserAccountPrincipal(actor))).param("status","PENDING"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.items[0].values.signedAmount").value("-1.20"));
    }
    @Test void openTitlesDetailMatchesAgingWithAsOfCompetenceAndPagination()throws Exception{
        LocalDate base=LocalDate.of(2026,7,1);var late=entry(owner,bank);var future=entry(owner,bank);var settled=entry(owner,bank);
        jdbc.update("UPDATE financial_entries SET due_date=?,payment_date=? WHERE id=?",base.minusDays(31),base.plusDays(1),late.getId());
        jdbc.update("UPDATE financial_entries SET due_date=?,competence='2026-08' WHERE id=?",base.plusDays(31),future.getId());jdbc.update("UPDATE financial_entries SET payment_date=? WHERE id=?",base,settled.getId());
        var first=openTitles.list(actor.getId(),owner.getId(),FinancialDirection.EXPENSE,base,com.finemp.finance.reporting.OpenTitlesAgingService.Scope.ALL,0,1);
        assertThat(first.totalItems()).isEqualTo(3);assertThat(first.items().getFirst().id()).isEqualTo(late.getId());assertThat(first.items().getFirst().daysLate()).isEqualTo(31);assertThat(first.items().getFirst().band()).isEqualTo(com.finemp.finance.reporting.OpenTitlesAgingService.Band.OVERDUE_31_60);
        var second=openTitles.list(actor.getId(),owner.getId(),FinancialDirection.EXPENSE,base,com.finemp.finance.reporting.OpenTitlesAgingService.Scope.ALL,1,1);assertThat(second.items().getFirst().band()).isEqualTo(com.finemp.finance.reporting.OpenTitlesAgingService.Band.DUE_0_30);
        assertThat(openTitles.list(actor.getId(),owner.getId(),FinancialDirection.EXPENSE,base,com.finemp.finance.reporting.OpenTitlesAgingService.Scope.COMPETENCE,0,20).totalItems()).isEqualTo(2);
        assertThat(first.totalItems()).isEqualTo(aging.report(actor.getId(),owner.getId(),FinancialDirection.EXPENSE,base,com.finemp.finance.reporting.OpenTitlesAgingService.Scope.ALL).totalCount());
        assertThatThrownBy(()->openTitles.list(actor.getId(),other.getId(),FinancialDirection.EXPENSE,base,com.finemp.finance.reporting.OpenTitlesAgingService.Scope.ALL,0,10)).isInstanceOf(OrganizationAccessDeniedException.class);
        var route="/api/companies/"+owner.getId()+"/financial-reports/open-titles";
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(route).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(new com.finemp.security.UserAccountPrincipal(actor))).param("direction","EXPENSE").param("asOf","2026-07-01").param("size","1"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.items[0].nominalAmount").value("1.00"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(route).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(new com.finemp.security.UserAccountPrincipal(actor))).param("direction","EXPENSE").param("asOf","2026-07-01").param("band","DUE_31_60").param("size","1"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.totalItems").value(1)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.items[0].id").value(future.getId().toString()));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(route).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(new com.finemp.security.UserAccountPrincipal(actor))).param("direction","EXPENSE").param("asOf","2026-07-01").param("band","UNKNOWN"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
    }
    @Test void closedLinkedStatementMustBlockUpdateReversalAndDeletionAtomically(){
        LocalDate august=LocalDate.of(2026,8,1);
        jdbc.update("UPDATE financial_entries SET competence='2026-08',payment_date=? WHERE id=?",august,entry.getId());
        var linked=save(owner,bank,value("-1","Closed linked statement","CLOSED-LINK"));
        jdbc.update("UPDATE financial_bank_statement_items SET status='RECONCILED',linked_entry_id=? WHERE id=?",entry.getId(),linked.getId());
        jdbc.update("INSERT INTO financial_periods(id,company_id,period,closed,version,updated_by,updated_at) VALUES(?,?,'2026-07',true,0,?,now())",UUID.randomUUID(),owner.getId(),actor.getId());
        var update=new FinancialEntryCommandData(FinancialDirection.EXPENSE,"Changed",BigDecimal.ONE,august,august,YearMonth.of(2026,8),august,august,bank.getId(),categoryIds.get(owner.getId()),null,null,null,"","","");
        assertThatThrownBy(()->entryCommands.update(actor.getId(),owner.getId(),UUID.randomUUID(),entry.getId(),0L,update)).isInstanceOf(com.finemp.finance.period.FinancialPeriodClosedException.class);
        assertThatThrownBy(()->settlements.reverse(actor.getId(),owner.getId(),UUID.randomUUID(),entry.getId(),0L)).isInstanceOf(com.finemp.finance.period.FinancialPeriodClosedException.class);
        assertThatThrownBy(()->deletions.delete(actor.getId(),owner.getId(),UUID.randomUUID(),entry.getId(),0L)).isInstanceOf(com.finemp.finance.period.FinancialPeriodClosedException.class);
        var unchanged=entries.findById(entry.getId()).orElseThrow();assertThat(unchanged.getVersion()).isZero();assertThat(unchanged.getValues().paymentDate()).isEqualTo(august);assertThat(unchanged.getValues().description()).isEqualTo("Synthetic statement entry");
        assertThat(statements.findById(linked.getId()).orElseThrow().getLinkedEntryId()).isEqualTo(entry.getId());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM financial_entry_command_receipts WHERE company_id=?",Long.class,owner.getId())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM financial_entry_events WHERE company_id=?",Long.class,owner.getId())).isZero();
    }
    @Test void linkedPeriodLookupMustIgnoreOtherCompaniesAndUnlinkedEntries(){
        var linked=save(other,foreignBank,value("-1","Foreign closed statement","FOREIGN-CLOSED"));
        jdbc.update("UPDATE financial_bank_statement_items SET status='RECONCILED',linked_entry_id=? WHERE id=?",foreignEntry.getId(),linked.getId());
        jdbc.update("INSERT INTO financial_periods(id,company_id,period,closed,version,updated_by,updated_at) VALUES(?,?,'2026-07',true,0,?,now())",UUID.randomUUID(),other.getId(),actor.getId());
        var tx=new TransactionTemplate(transactions);
        tx.executeWithoutResult(status->{companies.findLockedById(owner.getId());entryGuard.requireLinkedStatementPeriodsOpen(owner.getId(),List.of(entry.getId(),foreignEntry.getId()));});
        assertThatThrownBy(()->tx.executeWithoutResult(status->{companies.findLockedById(other.getId());entryGuard.requireLinkedStatementPeriodsOpen(other.getId(),List.of(foreignEntry.getId()));})).isInstanceOf(com.finemp.finance.period.FinancialPeriodClosedException.class);
    }
    @Test void presentationKeepsOneSnapshotWhenPaymentCommitsBetweenBlocks(){
        org.mockito.Mockito.doAnswer(call->{
            Object firstBlock=call.callRealMethod();
            var concurrent=new TransactionTemplate(transactions);concurrent.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            concurrent.executeWithoutResult(status->jdbc.update("UPDATE financial_entries SET payment_date=DATE '2026-07-02',actual_paid_amount=2.00 WHERE id=?",entry.getId()));
            return firstBlock;
        }).when(dailySnapshotProbe).report(actor.getId(),owner.getId(),"2026-07");
        var snapshot=presentation.snapshot(actor.getId(),owner.getId(),"2026-07",LocalDate.of(2026,7,31),com.finemp.finance.reporting.OpenTitlesAgingService.Scope.ALL);
        assertThat(snapshot.partial()).isTrue();assertThat(snapshot.schemaVersion()).isEqualTo(1);assertThat(snapshot.companyId()).isEqualTo(owner.getId());
        assertThat(snapshot.cashMonth().days().getFirst().openExpense()).isEqualByComparingTo("1.00");
        assertThat(snapshot.cashYear().totalOpenExpense()).isEqualByComparingTo("1.00");assertThat(snapshot.cashYear().totalRealExpense()).isEqualByComparingTo("0.00");
        assertThat(snapshot.payables().totalAmount()).isEqualByComparingTo("1.00");assertThat(snapshot.receivables().totalAmount()).isEqualByComparingTo("0.00");
        assertThat(jdbc.queryForObject("SELECT actual_paid_amount FROM financial_entries WHERE id=?",BigDecimal.class,entry.getId())).isEqualByComparingTo("2.00");
        assertThat(aging.report(actor.getId(),owner.getId(),FinancialDirection.EXPENSE,LocalDate.of(2026,7,31),com.finemp.finance.reporting.OpenTitlesAgingService.Scope.ALL).totalCount()).isZero();
    }
    @Test void partnerSubtotalsSeparateHomonymsKeepUnassignedAndApplyTheSameFilters()throws Exception{
        var a=partner(owner,"12ABC34501DE35",true);var b=partner(owner,"98ZYX76543WV39",false);var foreign=partner(other,"12ABC34501DE35",true);
        var first=entry(owner,bank);var secondEntry=entry(owner,bank);var third=entry(owner,bank);var base=LocalDate.of(2026,7,1);
        jdbc.update("UPDATE financial_entries SET partner_id=?,nominal_amount=1.23,due_date=?,payment_date=? WHERE id=?",a.getId(),base.minusDays(31),base.plusDays(1),first.getId());
        jdbc.update("UPDATE financial_entries SET document_number='SYNTHETIC-DOC-41',document_date=? WHERE id=?",base.minusDays(40),first.getId());
        jdbc.update("UPDATE financial_entries SET partner_id=?,nominal_amount=0.10,due_date=?,competence='2026-08' WHERE id=?",a.getId(),base.plusDays(31),secondEntry.getId());
        jdbc.update("UPDATE financial_entries SET partner_id=?,nominal_amount=2.34 WHERE id=?",b.getId(),third.getId());
        jdbc.update("UPDATE financial_entries SET partner_id=?,nominal_amount=999.99 WHERE id=?",foreign.getId(),foreignEntry.getId());
        var scope=com.finemp.finance.reporting.OpenTitlesAgingService.Scope.ALL;
        var result=openTitles.byPartner(actor.getId(),owner.getId(),FinancialDirection.EXPENSE,base,scope,null,0,10);
        assertThat(result.totalItems()).isEqualTo(3);assertThat(result.items()).hasSize(3);
        assertThat(result.items().stream().map(com.finemp.finance.reporting.OpenTitlesReadService.PartnerTotal::nominalAmount).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("4.67");
        var subtotal=result.items().stream().filter(g->a.getId().equals(g.partnerId())).findFirst().orElseThrow();assertThat(subtotal.titleCount()).isEqualTo(2);assertThat(subtotal.nominalAmount()).isEqualByComparingTo("1.33");
        var detail=openTitles.list(actor.getId(),owner.getId(),FinancialDirection.EXPENSE,base,scope,null,a.getId(),false,0,10);
        assertThat(detail.totalItems()).isEqualTo(subtotal.titleCount());assertThat(detail.items().stream().map(com.finemp.finance.reporting.OpenTitlesReadService.View::nominalAmount).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo(subtotal.nominalAmount());
        var nextPage=openTitles.list(actor.getId(),owner.getId(),FinancialDirection.EXPENSE,base,scope,null,a.getId(),false,1,1);assertThat(nextPage.totalItems()).isEqualTo(2);assertThat(nextPage.items().getFirst().id()).isEqualTo(secondEntry.getId());
        assertThat(openTitles.list(actor.getId(),owner.getId(),FinancialDirection.EXPENSE,base,scope,null,foreign.getId(),false,0,10).totalItems()).isZero();
        var unassigned=openTitles.list(actor.getId(),owner.getId(),FinancialDirection.EXPENSE,base,scope,null,null,true,0,10);assertThat(unassigned.totalItems()).isEqualTo(1);assertThat(unassigned.items().getFirst().id()).isEqualTo(entry.getId());
        assertThat(result.items().getLast().partnerId()).isNull();assertThat(result.items().getLast().nominalAmount()).isEqualByComparingTo("1.00");
        assertThat(openTitles.byPartner(actor.getId(),owner.getId(),FinancialDirection.EXPENSE,base,scope,null,2,1).items().getFirst().partnerId()).isNull();
        var late=openTitles.byPartner(actor.getId(),owner.getId(),FinancialDirection.EXPENSE,base,scope,com.finemp.finance.reporting.OpenTitlesAgingService.Band.OVERDUE_31_60,0,10);
        assertThat(late.totalItems()).isEqualTo(1);assertThat(late.items().getFirst().nominalAmount()).isEqualByComparingTo("1.23");
        var comp=openTitles.byPartner(actor.getId(),owner.getId(),FinancialDirection.EXPENSE,base,com.finemp.finance.reporting.OpenTitlesAgingService.Scope.COMPETENCE,null,0,10);
        assertThat(comp.items().stream().filter(g->a.getId().equals(g.partnerId())).findFirst().orElseThrow().nominalAmount()).isEqualByComparingTo("1.23");
        assertThatThrownBy(()->openTitles.byPartner(actor.getId(),other.getId(),FinancialDirection.EXPENSE,base,scope,null,0,10)).isInstanceOf(OrganizationAccessDeniedException.class);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/companies/"+owner.getId()+"/financial-reports/open-titles/by-partner").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(new com.finemp.security.UserAccountPrincipal(actor))).param("direction","EXPENSE").param("asOf","2026-07-01").param("band","OVERDUE_31_60"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.items[0].nominalAmount").value("1.23"));
        String route="/api/companies/"+owner.getId()+"/financial-reports/open-titles";
        var principal=org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(new com.finemp.security.UserAccountPrincipal(actor));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(route).with(principal).param("direction","EXPENSE").param("asOf","2026-07-01").param("partnerId",a.getId().toString()).param("band","OVERDUE_31_60"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.totalItems").value(1)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.items[0].id").value(first.getId().toString()))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.items[0].documentNumber").value("SYNTHETIC-DOC-41"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.items[0].documentDate").value(base.minusDays(40).toString()))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.items[0].categoryId").value(categoryIds.get(owner.getId()).toString()))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.items[0].nominalAmount").value("1.23"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(route).with(principal).param("direction","EXPENSE").param("asOf","2026-07-01").param("partnerId",a.getId().toString()).param("unassignedPartner","true"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
    }
    BusinessPartner partner(Company company,String document,boolean active){return partners.saveAndFlush(new BusinessPartner(company,new BusinessPartnerProfile(PartnerPersonType.LEGAL_ENTITY,document,"Synthetic homonym","",PartnerRole.BOTH,"","","","","","","","","","","","","","",active)));}
    @Test void presentationHttpRequiresExplicitInputsAndReturnsExactPartialBlocks()throws Exception{
        String route="/api/companies/"+owner.getId()+"/financial-reports/presentation-snapshot";
        var principal=org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(new com.finemp.security.UserAccountPrincipal(actor));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(route).with(principal).param("month","2026-07").param("asOf","2026-07-31").param("scope","ALL"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control","no-store"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.partial").value(true))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.cashYear.totalOpenExpense").value("1.00"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.payables.totalAmount").value("1.00"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.receivables.totalAmount").value("0.00"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(route).with(principal).param("month","2026-07").param("asOf","2026-07-31"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(route).with(principal).param("month","0000-07").param("asOf","2026-07-31").param("scope","ALL"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/companies/"+other.getId()+"/financial-reports/presentation-snapshot").with(principal).param("month","2026-07").param("asOf","2026-07-31").param("scope","ALL"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
    }
    @Test void presentationRequiresExplicitParametersPermissionAndConsistentIsolation(){
        var scope=com.finemp.finance.reporting.OpenTitlesAgingService.Scope.ALL;var date=LocalDate.of(2026,7,31);
        assertThatThrownBy(()->presentation.snapshot(actor.getId(),other.getId(),"2026-07",date,scope)).isInstanceOf(OrganizationAccessDeniedException.class);
        assertThatThrownBy(()->presentation.snapshot(actor.getId(),owner.getId(),null,date,scope)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->presentation.snapshot(actor.getId(),owner.getId(),"2026-07",null,scope)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->presentation.snapshot(actor.getId(),owner.getId(),"2026-07",date,null)).isInstanceOf(IllegalArgumentException.class);
        var weak=new TransactionTemplate(transactions);weak.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
        assertThatThrownBy(()->weak.execute(status->presentation.snapshot(actor.getId(),owner.getId(),"2026-07",date,scope))).isInstanceOf(IllegalStateException.class);
    }
    void receipt(Company company,BankAccount bank,UUID command){jdbc.update("INSERT INTO financial_statement_import_receipts(company_id,actor_id,command_id,bank_account_id,fingerprint,result_state,committed_at) VALUES (?,?,?,?,?,?::text,now())",company.getId(),actor.getId(),command,bank.getId(),"a".repeat(64),"{}");}
    void fails(String sql,Object...args){assertThatThrownBy(()->new TransactionTemplate(transactions).execute(status->jdbc.update(sql,args))).isInstanceOf(DataIntegrityViolationException.class);}
    BankStatementItem save(Company company,BankAccount bank,StatementValues values){return statements.saveAndFlush(new BankStatementItem(company,bank,values,actor.getId()));}
    StatementValues value(String amount,String memo,String fit){return new StatementValues(LocalDate.of(2026,7,1),new BigDecimal(amount),memo,fit,"");}
    BankAccount bank(Company company,String name){return banks.saveAndFlush(new BankAccount(company,"Synthetic Statement "+name,BankAccountType.CASH,BigDecimal.ZERO,LocalDate.of(2026,1,1),true));}
    FinancialEntry entry(Company company,BankAccount bank){var date=LocalDate.of(2026,7,1);var category=categoryIds.computeIfAbsent(company.getId(),id->{var group=categories.saveAndFlush(FinancialCategory.group(company,FinancialDirection.EXPENSE,1,"Synthetic Statement Group"));return categories.saveAndFlush(FinancialCategory.subcategory(group,1,"Synthetic Statement Category")).getId();});return entries.saveAndFlush(new FinancialEntry(company,new FinancialEntryValues(FinancialDirection.EXPENSE,"Synthetic statement entry",BigDecimal.ONE,date,date,YearMonth.of(2026,7),date,null,null,bank.getId(),category,null,null,null,"","",""),actor.getId()));}
    Company company(String name,String cnpj){var firm=firms.saveAndFlush(new AccountingFirm("Synthetic Statement Firm "+name,true));firmIds.add(firm.getId());var client=clients.saveAndFlush(new Client(firm,"Synthetic Statement Client "+name,true));clientIds.add(client.getId());return companies.saveAndFlush(new Company(client,"Synthetic Statement Company "+name,cnpj));}
}
