package com.finemp.finance.transfer;

import com.finemp.TestcontainersConfiguration;
import com.finemp.access.*;
import com.finemp.accountingfirm.*;
import com.finemp.administration.*;
import com.finemp.client.*;
import com.finemp.company.*;
import com.finemp.finance.bank.*;
import java.math.BigDecimal;
import java.time.LocalDate;
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

@SpringBootTest(properties={"finemp.finance.transfers.enabled=true","finemp.finance.catalog.enabled=true"})
@Import(TestcontainersConfiguration.class)
class BankTransferCommandIntegrationTest {
    @Autowired BankTransferCommandService service;@Autowired BankTransferRepository transfers;
    @Autowired BankAccountRepository banks;@Autowired CompanyRepository companies;@Autowired ClientRepository clients;@Autowired AccountingFirmRepository firms;
    @Autowired UserAccountRepository users;@Autowired CompanyAccessGrantRepository grants;
    @Autowired JdbcTemplate jdbc;@Autowired PlatformTransactionManager transactions;
    Company owner,other;UserAccount manager,viewer,restricted;
    UUID bankA,bankB,spare,foreignBank;
    final List<UUID> firmIds=new ArrayList<>(),clientIds=new ArrayList<>(),userIds=new ArrayList<>();
    @BeforeEach void prepare() {
        owner=company("Owner","81818181000181");other=company("Other","82828282000182");
        manager=user("Manager");viewer=user("Viewer");restricted=user("Restricted");
        grant(manager,CostCenterScope.ALL,CompanyPermission.BANK_ACCOUNT_MANAGE);grant(viewer,CostCenterScope.ALL,CompanyPermission.BANK_ACCOUNT_VIEW);
        grant(restricted,CostCenterScope.RESTRICTED,CompanyPermission.BANK_ACCOUNT_MANAGE);
        bankA=bank(owner,"A");bankB=bank(owner,"B");spare=bank(owner,"Spare");foreignBank=bank(other,"Foreign");
    }
    @AfterEach void cleanSyntheticFixtures() {
        for(var company:List.of(owner,other)) {
            for(var table:List.of("financial_bank_transfer_events","financial_bank_transfer_command_receipts","financial_bank_transfers","financial_bank_accounts"))
                jdbc.update("DELETE FROM "+table+" WHERE company_id=?",company.getId());
            jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id IN (SELECT id FROM company_access_grants WHERE company_id=?)",company.getId());
            jdbc.update("DELETE FROM company_access_grants WHERE company_id=?",company.getId());jdbc.update("DELETE FROM companies WHERE id=?",company.getId());
        }
        for(var id:clientIds)jdbc.update("DELETE FROM clients WHERE id=?",id);for(var id:firmIds)jdbc.update("DELETE FROM accounting_firms WHERE id=?",id);for(var id:userIds)jdbc.update("DELETE FROM user_accounts WHERE id=?",id);
    }
    @Test void createAndDeleteMustCommitExactIndependentStateReceiptsAndBeforeAfterAudit() {
        var created=create(UUID.randomUUID(),values("1.2300"));assertThat(created.version()).isZero();
        var row=transfers.findById(created.transferId()).orElseThrow();assertThat(row.getValues().amount()).isEqualTo(new BigDecimal("1.23"));
        assertThat(row.getCreatedBy()).isEqualTo(manager.getId());assertThat(row.getCreatedAt()).isNotNull();
        assertThat(jdbc.queryForObject("SELECT after_state::jsonb->>'amount' FROM financial_bank_transfer_events WHERE company_id=?",String.class,owner.getId())).isEqualTo("1.23");
        var deleted=delete(UUID.randomUUID(),created.transferId(),0);assertThat(deleted.action()).isEqualTo(BankTransferCommandAction.DELETED);
        assertThat(transfers.findById(created.transferId())).isEmpty();assertThat(count("financial_bank_transfer_events")).isEqualTo(2);assertThat(count("financial_bank_transfer_command_receipts")).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT before_state::jsonb->>'amount' FROM financial_bank_transfer_events WHERE company_id=? AND action='DELETED'",String.class,owner.getId())).isEqualTo("1.23");
        assertThat(jdbc.queryForObject("SELECT after_state IS NULL AND after_version IS NULL FROM financial_bank_transfer_events WHERE company_id=? AND action='DELETED'",Boolean.class,owner.getId())).isTrue();
        assertThat(count("financial_entries")).isZero();assertThat(banks.findById(bankA).orElseThrow().getOpeningBalance()).isEqualByComparingTo("5.00");
    }
    @Test void replayMustSurviveDeletionAndBankChangesButStillRequireCurrentPermission() {
        var key=UUID.randomUUID();var created=create(key,values("1.23"));var deleteKey=UUID.randomUUID();var deleted=delete(deleteKey,created.transferId(),0);
        jdbc.update("UPDATE financial_bank_accounts SET active=false WHERE company_id=?",owner.getId());
        assertThat(create(key,values("1.23"))).isEqualTo(created);assertThat(delete(deleteKey,created.transferId(),0)).isEqualTo(deleted);
        assertThat(count("financial_bank_transfers")).isZero();assertThat(count("financial_bank_transfer_events")).isEqualTo(2);
        revoke();assertThatThrownBy(()->create(key,values("1.23"))).isInstanceOf(OrganizationAccessDeniedException.class);
        assertThatThrownBy(()->delete(deleteKey,created.transferId(),0)).isInstanceOf(OrganizationAccessDeniedException.class);
    }
    @Test void changedPayloadActionOrExpectedVersionWithTheSameKeyMustConflict() {
        var key=UUID.randomUUID();var created=create(key,values("1.23"));
        assertThatThrownBy(()->create(key,values("1.24"))).isInstanceOf(BankTransferCommandConflictException.class);
        assertThatThrownBy(()->delete(key,created.transferId(),0)).isInstanceOf(BankTransferCommandConflictException.class);
        var deleteKey=UUID.randomUUID();delete(deleteKey,created.transferId(),0);
        assertThatThrownBy(()->delete(deleteKey,created.transferId(),1)).isInstanceOf(BankTransferCommandConflictException.class);
        assertThat(count("financial_bank_transfer_events")).isEqualTo(2);
    }
    @Test void permissionsBothReferencesAndTargetsMustRemainIsolatedAcrossFirmsAndClients() {
        for(var actor:List.of(viewer,restricted))assertThatThrownBy(()->service.create(actor.getId(),owner.getId(),UUID.randomUUID(),values("1"))).isInstanceOf(OrganizationAccessDeniedException.class);
        for(var pair:List.of(new UUID[]{foreignBank,bankB},new UUID[]{bankA,foreignBank}))
            assertThatThrownBy(()->create(UUID.randomUUID(),new BankTransferValues(pair[0],pair[1],LocalDate.of(2026,7,1),BigDecimal.ONE,""))).isInstanceOf(IllegalArgumentException.class);
        var foreignSecond=bank(other,"Foreign second");
        var foreign=transfers.saveAndFlush(new BankTransfer(other,new BankTransferValues(foreignBank,foreignSecond,LocalDate.of(2026,7,1),BigDecimal.ONE,""),manager.getId()));
        assertThatThrownBy(()->delete(UUID.randomUUID(),foreign.getId(),0)).isInstanceOf(SaasResourceNotFoundException.class);
        assertThatThrownBy(()->service.delete(manager.getId(),other.getId(),UUID.randomUUID(),foreign.getId(),0L)).isInstanceOf(OrganizationAccessDeniedException.class);
        assertThat(transfers.findById(foreign.getId())).isPresent();assertThat(count("financial_bank_transfer_events")).isZero();
    }
    @Test void creationNeedsTwoActiveAccountsWithoutInventingABlanketInactiveSelectionVeto() {
        jdbc.update("UPDATE financial_bank_accounts SET active=false WHERE id=?",bankB);
        assertThat(create(UUID.randomUUID(),values("1"))).isNotNull();
        jdbc.update("UPDATE financial_bank_accounts SET active=false WHERE id=?",spare);
        assertThatThrownBy(()->create(UUID.randomUUID(),values("1"))).isInstanceOf(BankTransferCommandConflictException.class)
                .extracting("reason").isEqualTo(BankTransferCommandConflictException.Reason.INSUFFICIENT_ACTIVE_ACCOUNTS);
        assertThat(count("financial_bank_transfers")).isEqualTo(1);
    }
    @Test void creationFailureAfterStateAndEventMustRollbackEverythingAndAllowSameKeyRetry() {
        var key=UUID.randomUUID();
        assertThatThrownBy(()->new TransactionTemplate(transactions).execute(status->{
            jdbc.execute("ALTER TABLE financial_bank_transfer_command_receipts ADD CONSTRAINT synthetic_transfer_create_failure CHECK(command_id <> '"+key+"'::uuid)");
            return create(key,values("1.23"));
        })).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(count("financial_bank_transfers")).isZero();assertThat(count("financial_bank_transfer_events")).isZero();assertThat(count("financial_bank_transfer_command_receipts")).isZero();
        assertThat(create(key,values("1.23"))).isNotNull();
    }
    @Test void deletionFailureAfterRemovalAndEventMustRestoreStateAndAllowSameKeyRetry() {
        var created=create(UUID.randomUUID(),values("1.23"));var key=UUID.randomUUID();
        assertThatThrownBy(()->new TransactionTemplate(transactions).execute(status->{
            jdbc.execute("ALTER TABLE financial_bank_transfer_command_receipts ADD CONSTRAINT synthetic_transfer_delete_failure CHECK(command_id <> '"+key+"'::uuid)");
            return delete(key,created.transferId(),0);
        })).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(transfers.findById(created.transferId())).isPresent();assertThat(count("financial_bank_transfer_events")).isEqualTo(1);assertThat(count("financial_bank_transfer_command_receipts")).isEqualTo(1);
        assertThat(delete(key,created.transferId(),0).action()).isEqualTo(BankTransferCommandAction.DELETED);
    }
    @Test void concurrentIdenticalCreatesMustCommitOnlyOneTransferEventAndReceipt()throws Exception {
        var key=UUID.randomUUID();var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            Callable<BankTransferCommandReceipt> task=()->{await(start);return create(key,values("1.23"));};
            var a=pool.submit(task);var b=pool.submit(task);start.countDown();assertThat(a.get(15,TimeUnit.SECONDS)).isEqualTo(b.get(15,TimeUnit.SECONDS));
            assertThat(count("financial_bank_transfers")).isEqualTo(1);assertThat(count("financial_bank_transfer_events")).isEqualTo(1);assertThat(count("financial_bank_transfer_command_receipts")).isEqualTo(1);
        }finally {start.countDown();pool.shutdownNow();}
    }
    @Test void concurrentDeletesMustCommitOnlyOneDeletionAndPreserveBothReceipts()throws Exception {
        var created=create(UUID.randomUUID(),values("1.23"));var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            Callable<Boolean> task=()->{await(start);try {delete(UUID.randomUUID(),created.transferId(),0);return true;}catch(SaasResourceNotFoundException missing) {return false;}};
            var a=pool.submit(task);var b=pool.submit(task);start.countDown();assertThat(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
            assertThat(count("financial_bank_transfers")).isZero();assertThat(count("financial_bank_transfer_events")).isEqualTo(2);assertThat(count("financial_bank_transfer_command_receipts")).isEqualTo(2);
        }finally {start.countDown();pool.shutdownNow();}
    }
    @Test void staleExpectedOrManagedVersionsAndUnsupportedIsolationMustFailBeforeDeleting() {
        var created=create(UUID.randomUUID(),values("1.23"));jdbc.update("UPDATE financial_bank_transfers SET version=1 WHERE id=?",created.transferId());
        assertThatThrownBy(()->delete(UUID.randomUUID(),created.transferId(),0)).isInstanceOf(BankTransferCommandConflictException.class);
        assertThatThrownBy(()->new TransactionTemplate(transactions).execute(status->{
            assertThat(transfers.findById(created.transferId()).orElseThrow().getVersion()).isEqualTo(1);
            jdbc.update("UPDATE financial_bank_transfers SET version=2 WHERE id=?",created.transferId());
            return delete(UUID.randomUUID(),created.transferId(),2);
        })).isInstanceOf(BankTransferCommandConflictException.class);
        var repeatable=new TransactionTemplate(transactions);repeatable.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        assertThatThrownBy(()->repeatable.execute(status->delete(UUID.randomUUID(),created.transferId(),1))).isInstanceOf(BankTransferCommandConflictException.class);
        assertThat(transfers.findById(created.transferId()).orElseThrow().getVersion()).isEqualTo(1);assertThat(count("financial_bank_transfer_events")).isEqualTo(1);
    }
    @Test void inactiveActorAndRevokedGrantInAnAlreadyLoadedPersistenceContextMustBeDenied() {
        new TransactionTemplate(transactions).executeWithoutResult(status->{
            grants.findByUserAccountIdAndCompanyId(manager.getId(),owner.getId()).orElseThrow().getPermissions().size();
            revoke();assertThatThrownBy(()->create(UUID.randomUUID(),values("1"))).isInstanceOf(OrganizationAccessDeniedException.class);status.setRollbackOnly();
        });
        jdbc.update("UPDATE user_accounts SET active=false WHERE id=?",manager.getId());
        assertThatThrownBy(()->create(UUID.randomUUID(),values("1"))).isInstanceOf(OrganizationAccessDeniedException.class);assertThat(count("financial_bank_transfers")).isZero();
    }
    @Test void currentCapturePreconditionMustNotTrustAnAlreadyLoadedBankSnapshot() {
        assertThatThrownBy(()->new TransactionTemplate(transactions).execute(status->{
            assertThat(banks.findById(bankA).orElseThrow().isActive()).isTrue();assertThat(banks.findById(bankB).orElseThrow().isActive()).isTrue();
            jdbc.update("UPDATE financial_bank_accounts SET active=false WHERE id IN (?,?)",bankA,bankB);
            return create(UUID.randomUUID(),values("1"));
        })).isInstanceOf(BankTransferCommandConflictException.class).extracting("reason").isEqualTo(BankTransferCommandConflictException.Reason.INSUFFICIENT_ACTIVE_ACCOUNTS);
        assertThat(count("financial_bank_transfers")).isZero();assertThat(count("financial_bank_transfer_events")).isZero();
    }
    @Test void databaseAuditTransitionConstraintMustRejectMissingCreatedVersionAndInvalidDeletedState() {
        var created=create(UUID.randomUUID(),values("1.23"));
        new TransactionTemplate(transactions).executeWithoutResult(status->{
            assertThatThrownBy(()->jdbc.update("UPDATE financial_bank_transfer_events SET after_version=NULL WHERE company_id=? AND action='CREATED'",owner.getId()))
                    .isInstanceOf(DataIntegrityViolationException.class);status.setRollbackOnly();
        });
        delete(UUID.randomUUID(),created.transferId(),0);
        new TransactionTemplate(transactions).executeWithoutResult(status->{
            assertThatThrownBy(()->jdbc.update("UPDATE financial_bank_transfer_events SET after_state='{}' WHERE company_id=? AND action='DELETED'",owner.getId()))
                    .isInstanceOf(DataIntegrityViolationException.class);status.setRollbackOnly();
        });
        assertThat(count("financial_bank_transfer_events")).isEqualTo(2);
    }
    BankTransferValues values(String amount) {return new BankTransferValues(bankA,bankB,LocalDate.of(2026,7,1),new BigDecimal(amount)," Synthetic transfer ");}
    BankTransferCommandReceipt create(UUID key,BankTransferValues values) {return service.create(manager.getId(),owner.getId(),key,values);}
    BankTransferCommandReceipt delete(UUID key,UUID transfer,long version) {return service.delete(manager.getId(),owner.getId(),key,transfer,version);}
    long count(String table) {return jdbc.queryForObject("SELECT count(*) FROM "+table+" WHERE company_id=?",Long.class,owner.getId());}
    void revoke() {jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id IN (SELECT id FROM company_access_grants WHERE company_id=? AND user_account_id=?)",owner.getId(),manager.getId());}
    void await(CountDownLatch start)throws InterruptedException {if(!start.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Synthetic barrier");}
    UUID bank(Company company,String label) {return banks.saveAndFlush(new BankAccount(company,"Synthetic Transfer "+label,BankAccountType.CASH,new BigDecimal("5.00"),LocalDate.of(2026,1,1),true)).getId();}
    Company company(String label,String cnpj) {var firm=firms.saveAndFlush(new AccountingFirm("Synthetic Transfer Command Firm "+label,true));firmIds.add(firm.getId());var client=clients.saveAndFlush(new Client(firm,"Synthetic Transfer Command Client "+label,true));clientIds.add(client.getId());return companies.saveAndFlush(new Company(client,"Synthetic Transfer Command Company "+label,cnpj));}
    UserAccount user(String label) {var actor=users.saveAndFlush(new UserAccount("Synthetic Transfer Command "+label,UUID.randomUUID()+"@example.invalid","hash"));userIds.add(actor.getId());return actor;}
    void grant(UserAccount actor,CostCenterScope scope,CompanyPermission permission) {grants.saveAndFlush(new CompanyAccessGrant(actor,owner,CompanyAccessRole.CUSTOM,scope,Set.of(permission)));}
}
