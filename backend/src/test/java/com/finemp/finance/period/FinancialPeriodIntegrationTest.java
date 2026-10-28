package com.finemp.finance.period;

import com.finemp.TestcontainersConfiguration;
import com.finemp.access.*;
import com.finemp.accountingfirm.*;
import com.finemp.administration.OrganizationAccessDeniedException;
import com.finemp.client.*;
import com.finemp.company.*;
import java.time.YearMonth;
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

@SpringBootTest(properties="finemp.finance.periods.enabled=true")
@Import(TestcontainersConfiguration.class)
class FinancialPeriodIntegrationTest {
    @Autowired FinancialPeriodCommandService service;@Autowired FinancialPeriodGuard guard;
    @Autowired CompanyRepository companies;@Autowired ClientRepository clients;@Autowired AccountingFirmRepository firms;
    @Autowired UserAccountRepository users;@Autowired CompanyAccessGrantRepository grants;@Autowired JdbcTemplate jdbc;@Autowired PlatformTransactionManager transactions;
    Company owner,other;UserAccount manager,closer,admin,restricted;
    final YearMonth july=YearMonth.of(2026,7);
    final List<UUID> firmIds=new ArrayList<>(),clientIds=new ArrayList<>(),userIds=new ArrayList<>();
    @BeforeEach void prepare() {
        owner=company("Owner","95959595000195");other=company("Other","96969696000196");
        manager=user("Manager");closer=user("Closer");admin=user("Admin");restricted=user("Restricted");
        grant(manager,CompanyAccessRole.CUSTOM,CostCenterScope.ALL,Set.of(CompanyPermission.FINANCIAL_PERIOD_CLOSE,CompanyPermission.FINANCIAL_PERIOD_REOPEN));
        grant(closer,CompanyAccessRole.CUSTOM,CostCenterScope.ALL,Set.of(CompanyPermission.FINANCIAL_PERIOD_CLOSE));
        grant(admin,CompanyAccessRole.ADMIN,CostCenterScope.ALL,Set.of());
        grant(restricted,CompanyAccessRole.CUSTOM,CostCenterScope.RESTRICTED,Set.of(CompanyPermission.FINANCIAL_PERIOD_CLOSE,CompanyPermission.FINANCIAL_PERIOD_REOPEN));
    }
    @AfterEach void cleanup() {
        for(var company:List.of(owner,other)) {
            jdbc.update("DELETE FROM financial_period_command_receipts WHERE company_id=?",company.getId());jdbc.update("DELETE FROM financial_periods WHERE company_id=?",company.getId());
            jdbc.update("DELETE FROM access_audit_events WHERE company_id=?",company.getId());
            jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id IN (SELECT id FROM company_access_grants WHERE company_id=?)",company.getId());
            jdbc.update("DELETE FROM company_access_grants WHERE company_id=?",company.getId());jdbc.update("DELETE FROM companies WHERE id=?",company.getId());
        }
        for(var id:clientIds)jdbc.update("DELETE FROM clients WHERE id=?",id);for(var id:firmIds)jdbc.update("DELETE FROM accounting_firms WHERE id=?",id);for(var id:userIds)jdbc.update("DELETE FROM user_accounts WHERE id=?",id);
    }
    @Test void closeAndReasonedReopenMustShareOfficialAuditAndReplayOriginalReceiptWithoutUndoingNewState() {
        var key=UUID.randomUUID();var first=close(key,july,null);assertThat(first.closed()).isTrue();assertThat(first.version()).isZero();
        var reopened=service.reopen(manager.getId(),owner.getId(),UUID.randomUUID(),july,0L,"  Ajuste autorizado  ");
        assertThat(reopened.periodId()).isEqualTo(first.periodId());assertThat(reopened.closed()).isFalse();assertThat(reopened.version()).isEqualTo(1);
        assertThat(close(key,july,null)).isEqualTo(first);assertThat(closed()).isFalse();
        assertThat(count("financial_periods")).isEqualTo(1);assertThat(count("financial_period_command_receipts")).isEqualTo(2);assertThat(count("access_audit_events")).isEqualTo(2);
        var audit=jdbc.queryForMap("SELECT action,resource_type,resource_id,command_id,before_state,after_state,reason FROM access_audit_events WHERE id=?",reopened.auditEventId());
        assertThat(audit.get("action")).isEqualTo("FINANCIAL_PERIOD_REOPENED");assertThat(audit.get("resource_type")).isEqualTo("FINANCIAL_PERIOD");assertThat(audit.get("resource_id")).isEqualTo(first.periodId());
        assertThat(audit.get("before_state").toString()).contains("\"closed\":true","\"version\":0");assertThat(audit.get("after_state").toString()).contains("\"closed\":false","\"version\":1");assertThat(audit.get("reason")).isEqualTo("Ajuste autorizado");
        assertThat(jdbc.queryForObject("SELECT permission FROM access_audit_event_permissions WHERE event_id=?",String.class,reopened.auditEventId())).isEqualTo("FINANCIAL_PERIOD_REOPEN");
    }
    @Test void explicitCurrentPermissionMustProtectBothActionsCompanyAndReplayWithoutRoleBypass() {
        for(var actor:List.of(admin,restricted))assertThatThrownBy(()->service.close(actor.getId(),owner.getId(),UUID.randomUUID(),july,null)).isInstanceOf(OrganizationAccessDeniedException.class);
        assertThatThrownBy(()->service.close(manager.getId(),other.getId(),UUID.randomUUID(),july,null)).isInstanceOf(OrganizationAccessDeniedException.class);
        var key=UUID.randomUUID();var first=service.close(closer.getId(),owner.getId(),key,july,null);
        assertThatThrownBy(()->service.reopen(closer.getId(),owner.getId(),UUID.randomUUID(),july,first.version(),"Motivo")).isInstanceOf(OrganizationAccessDeniedException.class);
        jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id IN (SELECT id FROM company_access_grants WHERE company_id=? AND user_account_id=?)",owner.getId(),closer.getId());
        assertThatThrownBy(()->service.close(closer.getId(),owner.getId(),key,july,null)).isInstanceOf(OrganizationAccessDeniedException.class);assertThat(count("access_audit_events")).isEqualTo(1);
    }
    @Test void reasonAndExpectedVersionMustBeValidatedWithoutOverwritingHistoryOrState() {
        close(UUID.randomUUID(),july,null);
        for(var reason:List.of(""," ","x".repeat(1001),"bad\u0000reason","bad\uD800reason"))
            assertThatThrownBy(()->service.reopen(manager.getId(),owner.getId(),UUID.randomUUID(),july,0L,reason)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.reopen(manager.getId(),owner.getId(),UUID.randomUUID(),july,1L,"Motivo")).isInstanceOfSatisfying(FinancialPeriodConflictException.class,e->assertThat(e.reason()).isEqualTo(FinancialPeriodConflictException.Reason.STALE_VERSION));
        assertThatThrownBy(()->close(UUID.randomUUID(),july,0L)).isInstanceOfSatisfying(FinancialPeriodConflictException.class,e->assertThat(e.reason()).isEqualTo(FinancialPeriodConflictException.Reason.ALREADY_CLOSED));
        assertThat(count("access_audit_events")).isEqualTo(1);assertThat(closed()).isTrue();
    }
    @Test void keyMustBeBoundToActionPeriodVersionAndReason() {
        var key=UUID.randomUUID();close(key,july,null);
        assertThatThrownBy(()->close(key,july.plusMonths(1),null)).isInstanceOf(FinancialPeriodConflictException.class);
        assertThatThrownBy(()->service.reopen(manager.getId(),owner.getId(),key,july,0L,"Motivo")).isInstanceOf(FinancialPeriodConflictException.class);
        var reopen=UUID.randomUUID();service.reopen(manager.getId(),owner.getId(),reopen,july,0L,"Primeiro motivo");
        assertThatThrownBy(()->service.reopen(manager.getId(),owner.getId(),reopen,july,0L,"Outro motivo")).isInstanceOf(FinancialPeriodConflictException.class);
        assertThat(count("financial_period_command_receipts")).isEqualTo(2);
    }
    @Test void failedReceiptMustRollbackStateOfficialAuditAndPermissionSnapshotThenPermitRetry() {
        var key=UUID.randomUUID();
        assertThatThrownBy(()->new TransactionTemplate(transactions).execute(status->{
            jdbc.execute("ALTER TABLE financial_period_command_receipts ADD CONSTRAINT synthetic_period_receipt_failure CHECK(command_id <> '"+key+"'::uuid)");return close(key,july,null);
        })).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(count("financial_periods")).isZero();assertThat(count("financial_period_command_receipts")).isZero();assertThat(count("access_audit_events")).isZero();
        assertThat(close(key,july,null).closed()).isTrue();
    }
    @Test void concurrentIdenticalCloseMustCommitOneStateAuditAndReceipt()throws Exception {
        var key=UUID.randomUUID();var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {Callable<FinancialPeriodReceipt> task=()->{if(!start.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Barrier");return close(key,july,null);};
            var a=pool.submit(task);var b=pool.submit(task);start.countDown();assertThat(a.get(15,TimeUnit.SECONDS)).isEqualTo(b.get(15,TimeUnit.SECONDS));
            assertThat(count("financial_periods")).isEqualTo(1);assertThat(count("access_audit_events")).isEqualTo(1);assertThat(count("financial_period_command_receipts")).isEqualTo(1);
        }finally {start.countDown();pool.shutdownNow();}
    }
    @Test void guardMustCheckEveryCurrentAffectedPeriodOnlyWithinTheCompanyAndReleaseAfterReopen() {
        close(UUID.randomUUID(),july,null);
        new TransactionTemplate(transactions).executeWithoutResult(status->{
            companies.findLockedById(owner.getId()).orElseThrow();guard.requireOpen(other.getId(),List.of(july));guard.requireOpen(owner.getId(),List.of(july.plusMonths(1)));
        });
        assertThatThrownBy(()->new TransactionTemplate(transactions).execute(status->{companies.findLockedById(owner.getId()).orElseThrow();guard.requireOpen(owner.getId(),List.of(july.plusMonths(1),july));return null;})).isInstanceOf(FinancialPeriodClosedException.class);
        service.reopen(manager.getId(),owner.getId(),UUID.randomUUID(),july,0L,"Correção autorizada");
        new TransactionTemplate(transactions).executeWithoutResult(status->{companies.findLockedById(owner.getId()).orElseThrow();guard.requireOpen(owner.getId(),List.of(july));});
    }
    @Test void unsupportedAmbientIsolationAndInvalidPeriodsMustPersistNothing() {
        var repeatable=new TransactionTemplate(transactions);repeatable.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        assertThatThrownBy(()->repeatable.execute(status->close(UUID.randomUUID(),july,null))).isInstanceOfSatisfying(FinancialPeriodConflictException.class,e->assertThat(e.reason()).isEqualTo(FinancialPeriodConflictException.Reason.TRANSACTION_NOT_SUPPORTED));
        for(var invalid:List.of(YearMonth.of(0,1),YearMonth.of(10000,1)))assertThatThrownBy(()->close(UUID.randomUUID(),invalid,null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(count("financial_periods")).isZero();
    }
    FinancialPeriodReceipt close(UUID key,YearMonth period,Long expected) {return service.close(manager.getId(),owner.getId(),key,period,expected);}
    boolean closed() {return jdbc.queryForObject("SELECT closed FROM financial_periods WHERE company_id=? AND period=?",Boolean.class,owner.getId(),july.toString());}
    long count(String table) {return jdbc.queryForObject("SELECT count(*) FROM "+table+" WHERE company_id=?",Long.class,owner.getId());}
    Company company(String name,String cnpj) {var firm=firms.saveAndFlush(new AccountingFirm("Synthetic Period Firm "+name,true));firmIds.add(firm.getId());var client=clients.saveAndFlush(new Client(firm,"Synthetic Period Client "+name,true));clientIds.add(client.getId());return companies.saveAndFlush(new Company(client,"Synthetic Period Company "+name,cnpj));}
    UserAccount user(String name) {var user=users.saveAndFlush(new UserAccount("Synthetic Period "+name,UUID.randomUUID()+"@example.invalid","hash"));userIds.add(user.getId());return user;}
    void grant(UserAccount actor,CompanyAccessRole role,CostCenterScope scope,Set<CompanyPermission> permissions) {grants.saveAndFlush(new CompanyAccessGrant(actor,owner,role,scope,permissions));}
}
