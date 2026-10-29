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

@SpringBootTest(properties={"finemp.finance.catalog.enabled=true","finemp.finance.statement-import.enabled=true","finemp.finance.statement-candidates.enabled=true","finemp.finance.aging.enabled=true","finemp.finance.monthly-cash.enabled=true","finemp.finance.statement-actions.enabled=true","finemp.finance.entries.enabled=true","finemp.finance.entries.commands.enabled=true"})
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class BankStatementPersistenceIntegrationTest {
    @Autowired BankStatementItemRepository statements;@Autowired BankAccountRepository banks;@Autowired BankAccountService bankService;
    @Autowired FinancialEntryRepository entries;@Autowired FinancialCategoryRepository categories;@Autowired CompanyRepository companies;@Autowired ClientRepository clients;@Autowired AccountingFirmRepository firms;@Autowired UserAccountRepository users;@Autowired CompanyAccessGrantRepository grants;
    @Autowired @org.springframework.beans.factory.annotation.Qualifier("financialStatementBankReferences") BankAccountReferenceGuard guard;
    @Autowired JdbcTemplate jdbc;@Autowired PlatformTransactionManager transactions;@Autowired StatementImportCommandService imports;@Autowired StatementCandidateReadService candidates;@Autowired StatementIgnoreCommandService ignores;@Autowired com.finemp.finance.reporting.OpenTitlesAgingService aging;@Autowired com.finemp.finance.reporting.MonthlyCashTotalsService monthly;@Autowired org.springframework.test.web.servlet.MockMvc mvc;
    Company owner,other;BankAccount bank,second,foreignBank;FinancialEntry entry,foreignEntry;UserAccount actor;final List<UUID> firmIds=new ArrayList<>(),clientIds=new ArrayList<>();final Map<UUID,UUID> categoryIds=new HashMap<>();
    @BeforeEach void prepare(){
        owner=company("Owner","70707070000170");other=company("Other","80808080000180");actor=users.saveAndFlush(new UserAccount("Synthetic Statement Actor",UUID.randomUUID()+"@example.invalid","hash"));
        grants.saveAndFlush(new CompanyAccessGrant(actor,owner,CompanyAccessRole.CUSTOM,CostCenterScope.ALL,Set.of(CompanyPermission.BANK_ACCOUNT_MANAGE,CompanyPermission.FINANCIAL_ENTRY_MANAGE)));
        bank=bank(owner,"Primary");second=bank(owner,"Secondary");foreignBank=bank(other,"Foreign");entry=entry(owner,bank);foreignEntry=entry(other,foreignBank);
    }
    @AfterEach void cleanup(){
        for(var company:List.of(owner,other)){
            for(var table:List.of("financial_statement_action_receipts","financial_statement_import_receipts","financial_bank_statement_items","financial_entries","financial_periods","financial_bank_accounts","financial_categories","access_audit_events"))jdbc.update("DELETE FROM "+table+" WHERE company_id=?",company.getId());
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
        assertThat(result.bands()).allSatisfy(band->assertThat(band.count()).isGreaterThanOrEqualTo(1));
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
    void receipt(Company company,BankAccount bank,UUID command){jdbc.update("INSERT INTO financial_statement_import_receipts(company_id,actor_id,command_id,bank_account_id,fingerprint,result_state,committed_at) VALUES (?,?,?,?,?,?::text,now())",company.getId(),actor.getId(),command,bank.getId(),"a".repeat(64),"{}");}
    void fails(String sql,Object...args){assertThatThrownBy(()->new TransactionTemplate(transactions).execute(status->jdbc.update(sql,args))).isInstanceOf(DataIntegrityViolationException.class);}
    BankStatementItem save(Company company,BankAccount bank,StatementValues values){return statements.saveAndFlush(new BankStatementItem(company,bank,values,actor.getId()));}
    StatementValues value(String amount,String memo,String fit){return new StatementValues(LocalDate.of(2026,7,1),new BigDecimal(amount),memo,fit,"");}
    BankAccount bank(Company company,String name){return banks.saveAndFlush(new BankAccount(company,"Synthetic Statement "+name,BankAccountType.CASH,BigDecimal.ZERO,LocalDate.of(2026,1,1),true));}
    FinancialEntry entry(Company company,BankAccount bank){var date=LocalDate.of(2026,7,1);var category=categoryIds.computeIfAbsent(company.getId(),id->{var group=categories.saveAndFlush(FinancialCategory.group(company,FinancialDirection.EXPENSE,1,"Synthetic Statement Group"));return categories.saveAndFlush(FinancialCategory.subcategory(group,1,"Synthetic Statement Category")).getId();});return entries.saveAndFlush(new FinancialEntry(company,new FinancialEntryValues(FinancialDirection.EXPENSE,"Synthetic statement entry",BigDecimal.ONE,date,date,YearMonth.of(2026,7),date,null,null,bank.getId(),category,null,null,null,"","",""),actor.getId()));}
    Company company(String name,String cnpj){var firm=firms.saveAndFlush(new AccountingFirm("Synthetic Statement Firm "+name,true));firmIds.add(firm.getId());var client=clients.saveAndFlush(new Client(firm,"Synthetic Statement Client "+name,true));clientIds.add(client.getId());return companies.saveAndFlush(new Company(client,"Synthetic Statement Company "+name,cnpj));}
}
