package com.finemp.finance.reconciliation;

import com.finemp.TestcontainersConfiguration;
import com.finemp.access.*;
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

@SpringBootTest(properties="finemp.finance.catalog.enabled=true")
@Import(TestcontainersConfiguration.class)
class BankStatementPersistenceIntegrationTest {
    @Autowired BankStatementItemRepository statements;@Autowired BankAccountRepository banks;@Autowired BankAccountService bankService;
    @Autowired FinancialEntryRepository entries;@Autowired FinancialCategoryRepository categories;@Autowired CompanyRepository companies;@Autowired ClientRepository clients;@Autowired AccountingFirmRepository firms;@Autowired UserAccountRepository users;@Autowired CompanyAccessGrantRepository grants;
    @Autowired @org.springframework.beans.factory.annotation.Qualifier("financialStatementBankReferences") BankAccountReferenceGuard guard;
    @Autowired JdbcTemplate jdbc;@Autowired PlatformTransactionManager transactions;
    Company owner,other;BankAccount bank,second,foreignBank;FinancialEntry entry,foreignEntry;UserAccount actor;final List<UUID> firmIds=new ArrayList<>(),clientIds=new ArrayList<>();
    @BeforeEach void prepare(){
        owner=company("Owner","70707070000170");other=company("Other","80808080000180");actor=users.saveAndFlush(new UserAccount("Synthetic Statement Actor",UUID.randomUUID()+"@example.invalid","hash"));
        grants.saveAndFlush(new CompanyAccessGrant(actor,owner,CompanyAccessRole.CUSTOM,CostCenterScope.ALL,Set.of(CompanyPermission.BANK_ACCOUNT_MANAGE)));
        bank=bank(owner,"Primary");second=bank(owner,"Secondary");foreignBank=bank(other,"Foreign");entry=entry(owner,bank);foreignEntry=entry(other,foreignBank);
    }
    @AfterEach void cleanup(){
        for(var company:List.of(owner,other)){
            for(var table:List.of("financial_bank_statement_items","financial_entries","financial_bank_accounts","financial_categories"))jdbc.update("DELETE FROM "+table+" WHERE company_id=?",company.getId());
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
        save(owner,bank,value("-1.20","TÁXÍ", ""));assertThatThrownBy(()->save(owner,bank,value("-1.2"," taxi ", ""))).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(statements.count()).isEqualTo(4);
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
    void fails(String sql,Object...args){assertThatThrownBy(()->new TransactionTemplate(transactions).execute(status->jdbc.update(sql,args))).isInstanceOf(DataIntegrityViolationException.class);}
    BankStatementItem save(Company company,BankAccount bank,StatementValues values){return statements.saveAndFlush(new BankStatementItem(company,bank,values,actor.getId()));}
    StatementValues value(String amount,String memo,String fit){return new StatementValues(LocalDate.of(2026,7,1),new BigDecimal(amount),memo,fit,"");}
    BankAccount bank(Company company,String name){return banks.saveAndFlush(new BankAccount(company,"Synthetic Statement "+name,BankAccountType.CASH,BigDecimal.ZERO,LocalDate.of(2026,1,1),true));}
    FinancialEntry entry(Company company,BankAccount bank){var date=LocalDate.of(2026,7,1);var group=categories.saveAndFlush(FinancialCategory.group(company,FinancialDirection.EXPENSE,1,"Synthetic Statement Group"));var sub=categories.saveAndFlush(FinancialCategory.subcategory(group,1,"Synthetic Statement Category"));return entries.saveAndFlush(new FinancialEntry(company,new FinancialEntryValues(FinancialDirection.EXPENSE,"Synthetic statement entry",BigDecimal.ONE,date,date,YearMonth.of(2026,7),date,null,null,bank.getId(),sub.getId(),null,null,null,"","",""),actor.getId()));}
    Company company(String name,String cnpj){var firm=firms.saveAndFlush(new AccountingFirm("Synthetic Statement Firm "+name,true));firmIds.add(firm.getId());var client=clients.saveAndFlush(new Client(firm,"Synthetic Statement Client "+name,true));clientIds.add(client.getId());return companies.saveAndFlush(new Company(client,"Synthetic Statement Company "+name,cnpj));}
}
