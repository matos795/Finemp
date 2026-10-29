package com.finemp.finance.withholding;

import com.finemp.TestcontainersConfiguration;
import com.finemp.access.*;
import com.finemp.accountingfirm.*;
import com.finemp.administration.*;
import com.finemp.client.*;
import com.finemp.company.*;
import com.finemp.finance.bank.*;
import com.finemp.finance.category.*;
import com.finemp.finance.entry.*;
import com.finemp.finance.period.*;
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

@SpringBootTest(properties={"finemp.finance.withholdings.enabled=true","finemp.finance.periods.enabled=true","finemp.finance.entries.enabled=true"})
@Import(TestcontainersConfiguration.class)
class WithholdingCommandIntegrationTest {
    @Autowired WithholdingCommandService service;@Autowired FinancialWithholdingRepository withholdings;@Autowired FinancialEntryRepository entries;@Autowired EntryDeletionCommandService deletion;@Autowired FinancialPeriodCommandService periods;
    @Autowired BankAccountRepository banks;@Autowired FinancialCategoryRepository categories;@Autowired CompanyRepository companies;@Autowired ClientRepository clients;@Autowired AccountingFirmRepository firms;@Autowired UserAccountRepository users;@Autowired CompanyAccessGrantRepository grants;
    @Autowired JdbcTemplate jdbc;@Autowired PlatformTransactionManager transactions;
    Company owner,other;UserAccount manager,viewer,restricted;FinancialEntry entry,foreign;final List<UUID> firmIds=new ArrayList<>(),clientIds=new ArrayList<>(),userIds=new ArrayList<>();
    @BeforeEach void prepare() {
        owner=company("Owner","30303030000130");other=company("Other","40404040000140");manager=user("Manager");viewer=user("Viewer");restricted=user("Restricted");
        grant(manager,CostCenterScope.ALL,Set.of(CompanyPermission.FINANCIAL_ENTRY_MANAGE,CompanyPermission.FINANCIAL_PERIOD_CLOSE,CompanyPermission.FINANCIAL_PERIOD_REOPEN));grant(viewer,CostCenterScope.ALL,Set.of(CompanyPermission.FINANCIAL_ENTRY_VIEW));grant(restricted,CostCenterScope.RESTRICTED,Set.of(CompanyPermission.FINANCIAL_ENTRY_MANAGE));entry=entry(owner);foreign=entry(other);
    }
    @AfterEach void cleanup() {
        for(var company:List.of(owner,other)) {
            for(var table:List.of("financial_withholding_command_receipts","financial_tax_withholdings","financial_entry_events","financial_entry_command_receipts","financial_entry_command_keys","financial_entries","financial_period_command_receipts","financial_periods","financial_bank_accounts","financial_categories","access_audit_events"))jdbc.update("DELETE FROM "+table+" WHERE company_id=?",company.getId());
            jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id IN (SELECT id FROM company_access_grants WHERE company_id=?)",company.getId());jdbc.update("DELETE FROM company_access_grants WHERE company_id=?",company.getId());companies.deleteById(company.getId());
        }
        for(var id:clientIds)clients.deleteById(id);for(var id:firmIds)firms.deleteById(id);for(var id:userIds)users.deleteById(id);
    }
    @Test void createUpdateDeleteMustUseOfficialAuditExactValuesAndNeverModifyTheParent() {
        var created=create(UUID.randomUUID());assertThat(created.version()).isZero();
        var updated=service.update(manager.getId(),owner.getId(),UUID.randomUUID(),entry.getId(),0L,created.withholdingId(),0L,new WithholdingValues("ISS",WithholdingTreatment.RECOVERABLE,new BigDecimal("2.34"),"Doc"));assertThat(updated.version()).isEqualTo(1);
        var audit=jdbc.queryForMap("SELECT resource_type,before_state,after_state FROM access_audit_events WHERE id=?",updated.auditEventId());assertThat(audit.get("resource_type")).isEqualTo("FINANCIAL_WITHHOLDING");assertThat(audit.get("before_state").toString()).contains("\"amount\":\"1.23\"");assertThat(audit.get("after_state").toString()).contains("RECOVERABLE","\"amount\":\"2.34\"");
        var deleted=service.delete(manager.getId(),owner.getId(),UUID.randomUUID(),entry.getId(),0L,created.withholdingId(),1L);assertThat(deleted.version()).isEqualTo(1);assertThat(count("financial_tax_withholdings")).isZero();assertThat(taxEvents()).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT after_state FROM access_audit_events WHERE id=?",String.class,deleted.auditEventId())).isNull();assertThat(entries.findById(entry.getId()).orElseThrow().getValues()).isEqualTo(entry.getValues());assertThat(entries.findById(entry.getId()).orElseThrow().getVersion()).isZero();
    }
    @Test void currentNativeVersionsMustRejectStaleParentAndChildDespiteCachedEntities() {
        var receipt=create(UUID.randomUUID());
        new TransactionTemplate(transactions).executeWithoutResult(status->{
            var cached=withholdings.findById(receipt.withholdingId()).orElseThrow();
            service.update(manager.getId(),owner.getId(),UUID.randomUUID(),entry.getId(),0L,receipt.withholdingId(),0L,new WithholdingValues("ISS",WithholdingTreatment.PAYABLE,new BigDecimal("2"),""));
            assertThat(cached.getVersion()).isZero();
        });
        assertThatThrownBy(()->service.update(manager.getId(),owner.getId(),UUID.randomUUID(),entry.getId(),0L,receipt.withholdingId(),0L,values())).isInstanceOfSatisfying(WithholdingConflictException.class,e->assertThat(e.reason()).isEqualTo(WithholdingConflictException.Reason.STALE_VERSION));
        jdbc.update("UPDATE financial_entries SET version=version+1 WHERE id=?",entry.getId());assertThatThrownBy(()->create(UUID.randomUUID())).isInstanceOfSatisfying(WithholdingConflictException.class,e->assertThat(e.reason()).isEqualTo(WithholdingConflictException.Reason.STALE_ENTRY_VERSION));
        assertThat(count("financial_tax_withholdings")).isEqualTo(1);
    }
    @Test void permissionScopeCompanyAndParentLinkMustBeAuthoritative() {
        for(var actor:List.of(viewer,restricted))assertThatThrownBy(()->service.create(actor.getId(),owner.getId(),UUID.randomUUID(),entry.getId(),0L,values())).isInstanceOf(OrganizationAccessDeniedException.class);
        assertThatThrownBy(()->service.create(manager.getId(),other.getId(),UUID.randomUUID(),foreign.getId(),0L,values())).isInstanceOf(OrganizationAccessDeniedException.class);
        assertThatThrownBy(()->service.create(manager.getId(),owner.getId(),UUID.randomUUID(),foreign.getId(),0L,values())).isInstanceOf(SaasResourceNotFoundException.class);
        var receipt=create(UUID.randomUUID());var another=entry(owner);
        assertThatThrownBy(()->service.delete(manager.getId(),owner.getId(),UUID.randomUUID(),another.getId(),0L,receipt.withholdingId(),0L)).isInstanceOf(SaasResourceNotFoundException.class);
        assertThat(count("financial_tax_withholdings")).isEqualTo(1);
    }
    @Test void closedCompetenceMustBlockAllNewEffectsButAllowReceiptReplay() {
        var key=UUID.randomUUID();var receipt=create(key);periods.close(manager.getId(),owner.getId(),UUID.randomUUID(),YearMonth.of(2026,7),null);
        assertThat(create(key)).isEqualTo(receipt);assertThatThrownBy(()->create(UUID.randomUUID())).isInstanceOf(FinancialPeriodClosedException.class);
        assertThatThrownBy(()->service.update(manager.getId(),owner.getId(),UUID.randomUUID(),entry.getId(),0L,receipt.withholdingId(),0L,values())).isInstanceOf(FinancialPeriodClosedException.class);
        assertThatThrownBy(()->service.delete(manager.getId(),owner.getId(),UUID.randomUUID(),entry.getId(),0L,receipt.withholdingId(),0L)).isInstanceOf(FinancialPeriodClosedException.class);
        assertThat(taxEvents()).isEqualTo(1);assertThat(count("financial_withholding_command_receipts")).isEqualTo(1);
    }
    @Test void replayMustSurviveRemovedChildAndParentButStillRequireCurrentPermission() {
        var key=UUID.randomUUID();var receipt=create(key);service.delete(manager.getId(),owner.getId(),UUID.randomUUID(),entry.getId(),0L,receipt.withholdingId(),0L);
        deletion.delete(manager.getId(),owner.getId(),UUID.randomUUID(),entry.getId(),0L);assertThat(create(key)).isEqualTo(receipt);assertThat(count("financial_tax_withholdings")).isZero();
        jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id IN (SELECT id FROM company_access_grants WHERE company_id=? AND user_account_id=?)",owner.getId(),manager.getId());assertThatThrownBy(()->create(key)).isInstanceOf(OrganizationAccessDeniedException.class);
    }
    @Test void changedCommandContentOrActionMustNotReuseAKey() {
        var key=UUID.randomUUID();var receipt=create(key);
        assertThatThrownBy(()->service.create(manager.getId(),owner.getId(),key,entry.getId(),0L,new WithholdingValues("IRRF",WithholdingTreatment.RECOVERABLE,new BigDecimal("1.23"),""))).isInstanceOf(WithholdingConflictException.class);
        assertThatThrownBy(()->service.delete(manager.getId(),owner.getId(),key,entry.getId(),0L,receipt.withholdingId(),0L)).isInstanceOf(WithholdingConflictException.class);assertThat(taxEvents()).isEqualTo(1);
    }
    @Test void failureAtReceiptMustRollbackCreateUpdateAndDeleteIncludingOfficialAudit() {
        var original=create(UUID.randomUUID());
        for(var action:WithholdingAction.values()) {
            var key=UUID.randomUUID();assertThatThrownBy(()->new TransactionTemplate(transactions).execute(status->{
                jdbc.execute("ALTER TABLE financial_withholding_command_receipts ADD CONSTRAINT synthetic_withholding_failure CHECK(command_id <> '"+key+"'::uuid)");
                return switch(action) {case CREATED->create(key);case UPDATED->service.update(manager.getId(),owner.getId(),key,entry.getId(),0L,original.withholdingId(),0L,new WithholdingValues("ISS",WithholdingTreatment.PAYABLE,BigDecimal.TEN,""));case DELETED->service.delete(manager.getId(),owner.getId(),key,entry.getId(),0L,original.withholdingId(),0L);};
            })).isInstanceOf(DataIntegrityViolationException.class);
            assertThat(count("financial_tax_withholdings")).isEqualTo(1);assertThat(taxEvents()).isEqualTo(1);assertThat(count("financial_withholding_command_receipts")).isEqualTo(1);assertThat(withholdings.findById(original.withholdingId()).orElseThrow().getVersion()).isZero();
        }
    }
    @Test void concurrentIdenticalCreateMustProduceOneRowAuditAndReceipt()throws Exception {
        var key=UUID.randomUUID();var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {Callable<WithholdingReceipt> task=()->{if(!start.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Barrier");return create(key);};var a=pool.submit(task);var b=pool.submit(task);start.countDown();assertThat(a.get(15,TimeUnit.SECONDS)).isEqualTo(b.get(15,TimeUnit.SECONDS));assertThat(count("financial_tax_withholdings")).isEqualTo(1);assertThat(taxEvents()).isEqualTo(1);assertThat(count("financial_withholding_command_receipts")).isEqualTo(1);}finally{start.countDown();pool.shutdownNow();}
    }
    @Test void unsupportedAmbientIsolationMustNotCreateAnyState() {
        var tx=new TransactionTemplate(transactions);tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);assertThatThrownBy(()->tx.execute(status->create(UUID.randomUUID()))).isInstanceOfSatisfying(WithholdingConflictException.class,e->assertThat(e.reason()).isEqualTo(WithholdingConflictException.Reason.TRANSACTION_NOT_SUPPORTED));assertThat(count("financial_tax_withholdings")).isZero();
    }
    WithholdingReceipt create(UUID key) {return service.create(manager.getId(),owner.getId(),key,entry.getId(),0L,values());}
    WithholdingValues values() {return new WithholdingValues("IRRF",WithholdingTreatment.PAYABLE,new BigDecimal("1.23"),"");}
    long count(String table) {return jdbc.queryForObject("SELECT count(*) FROM "+table+" WHERE company_id=?",Long.class,owner.getId());}
    long taxEvents() {return jdbc.queryForObject("SELECT count(*) FROM access_audit_events WHERE company_id=? AND resource_type='FINANCIAL_WITHHOLDING'",Long.class,owner.getId());}
    FinancialEntry entry(Company company) {var date=LocalDate.of(2026,7,1);var bank=banks.saveAndFlush(new BankAccount(company,"Synthetic Tax Bank",BankAccountType.CASH,BigDecimal.ZERO,date,true));var group=categories.saveAndFlush(FinancialCategory.group(company,FinancialDirection.EXPENSE,categories.countByCompanyId(company.getId())+1,"Synthetic Tax Group"));var sub=categories.saveAndFlush(FinancialCategory.subcategory(group,1,"Synthetic Tax Category"));return entries.saveAndFlush(new FinancialEntry(company,new FinancialEntryValues(FinancialDirection.EXPENSE,"Synthetic tax entry",BigDecimal.ONE,date,date,YearMonth.of(2026,7),date,null,null,bank.getId(),sub.getId(),null,null,null,"","",""),manager.getId()));}
    Company company(String name,String cnpj) {var firm=firms.saveAndFlush(new AccountingFirm("Synthetic Tax Firm "+name,true));firmIds.add(firm.getId());var client=clients.saveAndFlush(new Client(firm,"Synthetic Tax Client "+name,true));clientIds.add(client.getId());return companies.saveAndFlush(new Company(client,"Synthetic Tax Company "+name,cnpj));}
    UserAccount user(String name) {var user=users.saveAndFlush(new UserAccount("Synthetic Tax "+name,UUID.randomUUID()+"@example.invalid","hash"));userIds.add(user.getId());return user;}
    void grant(UserAccount actor,CostCenterScope scope,Set<CompanyPermission> permissions) {grants.saveAndFlush(new CompanyAccessGrant(actor,owner,CompanyAccessRole.CUSTOM,scope,permissions));}
}
