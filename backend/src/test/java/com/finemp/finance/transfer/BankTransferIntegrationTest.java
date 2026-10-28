package com.finemp.finance.transfer;

import com.finemp.TestcontainersConfiguration;
import com.finemp.access.*;
import com.finemp.accountingfirm.*;
import com.finemp.client.*;
import com.finemp.company.*;
import com.finemp.finance.bank.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={"finemp.finance.catalog.enabled=true","finemp.finance.entries.enabled=false","finemp.finance.transfers.enabled=false"})
@Import(TestcontainersConfiguration.class)
class BankTransferIntegrationTest {
    @Autowired BankTransferRepository transfers;
    @Autowired BankTransferBankReferences usage;
    @Autowired BankAccountRepository banks;
    @Autowired BankAccountService bankService;
    @Autowired CompanyRepository companies;
    @Autowired ClientRepository clients;
    @Autowired AccountingFirmRepository firms;
    @Autowired UserAccountRepository users;
    @Autowired CompanyAccessGrantRepository grants;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    private Company owner,other;
    private UserAccount actor;
    private UUID origin,destination,spare,foreignBank,transfer;
    private final List<UUID> firmIds=new ArrayList<>(),clientIds=new ArrayList<>();

    @BeforeEach void prepare() {
        owner=company("Owner","73737373000173");other=company("Other","74747474000174");
        actor=users.saveAndFlush(new UserAccount("Synthetic Transfer Manager",UUID.randomUUID()+"@example.invalid","hash"));
        grants.saveAndFlush(new CompanyAccessGrant(actor,owner,CompanyAccessRole.CUSTOM,CostCenterScope.ALL,Set.of(CompanyPermission.BANK_ACCOUNT_MANAGE)));
        origin=bank(owner,"Origin");destination=bank(owner,"Destination");spare=bank(owner,"Unused");foreignBank=bank(other,"Foreign");
        transfer=transfers.saveAndFlush(new BankTransfer(owner,values(origin,destination,new BigDecimal("1.2300")),actor.getId())).getId();
    }
    @AfterEach void cleanSyntheticFixtures() {
        for(var company:List.of(owner,other)) {
            jdbc.update("DELETE FROM financial_bank_transfers WHERE company_id=?",company.getId());
            jdbc.update("DELETE FROM financial_bank_accounts WHERE company_id=?",company.getId());
            jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id IN (SELECT id FROM company_access_grants WHERE company_id=?)",company.getId());
            jdbc.update("DELETE FROM company_access_grants WHERE company_id=?",company.getId());
            jdbc.update("DELETE FROM companies WHERE id=?",company.getId());
        }
        for(var id:clientIds)jdbc.update("DELETE FROM clients WHERE id=?",id);
        for(var id:firmIds)jdbc.update("DELETE FROM accounting_firms WHERE id=?",id);
        jdbc.update("DELETE FROM user_accounts WHERE id=?",actor.getId());
    }
    @Test void exactIndependentStateAndTraceabilityMustRoundTripWithoutArtificialEntryLegs() {
        var row=transfers.findByIdAndCompanyId(transfer,owner.getId()).orElseThrow();
        assertThat(row.getValues()).isEqualTo(values(origin,destination,new BigDecimal("1.23")));
        assertThat(row.getCreatedBy()).isEqualTo(actor.getId());assertThat(row.getCreatedAt()).isNotNull();assertThat(row.getVersion()).isZero();
        var zero=transfers.saveAndFlush(new BankTransfer(owner,values(destination,origin,BigDecimal.ZERO),actor.getId()));
        assertThat(transfers.findById(zero.getId()).orElseThrow().getValues().amount()).isEqualTo(new BigDecimal("0.00"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM financial_entries WHERE company_id=?",Long.class,owner.getId())).isZero();
        assertThat(banks.findById(origin).orElseThrow().getOpeningBalance()).isEqualByComparingTo("5.00");
        assertThat(banks.findById(destination).orElseThrow().getOpeningBalance()).isEqualByComparingTo("5.00");
    }
    @Test void ownedLookupAndBothReferenceEndsMustBeScopedByCompany() {
        assertThat(transfers.findByIdAndCompanyId(transfer,other.getId())).isEmpty();
        assertThat(usage.hasReferences(owner.getId(),origin)).isTrue();assertThat(usage.hasReferences(owner.getId(),destination)).isTrue();
        assertThat(usage.hasReferences(other.getId(),origin)).isFalse();assertThat(usage.hasReferences(other.getId(),destination)).isFalse();
        assertThat(usage.hasReferences(owner.getId(),foreignBank)).isFalse();assertThat(usage.hasReferences(owner.getId(),spare)).isFalse();
    }
    @Test void compositeForeignKeysMustRejectEitherCrossCompanyBank() {
        for(var pair:List.of(new UUID[]{foreignBank,destination},new UUID[]{origin,foreignBank})) {
            new TransactionTemplate(transactions).executeWithoutResult(status->{
                assertThatThrownBy(()->transfers.saveAndFlush(new BankTransfer(owner,values(pair[0],pair[1],BigDecimal.ONE),actor.getId())))
                        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
                status.setRollbackOnly();
            });
        }
    }
    @Test void actualBankServiceMustDeactivateBothUsedEndsEvenWithOperationalProducersDisabled() {
        assertThat(bankService.delete(actor.getId(),owner.getId(),origin,0).action()).isEqualTo(BankAccountService.DeleteAction.DEACTIVATED);
        assertThat(bankService.delete(actor.getId(),owner.getId(),destination,0).action()).isEqualTo(BankAccountService.DeleteAction.DEACTIVATED);
        assertThat(banks.findById(origin).orElseThrow().isActive()).isFalse();assertThat(banks.findById(destination).orElseThrow().isActive()).isFalse();
        assertThat(transfers.findById(transfer)).isPresent();
        assertThat(bankService.delete(actor.getId(),owner.getId(),spare,0).action()).isEqualTo(BankAccountService.DeleteAction.DELETED);
        transfers.deleteById(transfer);transfers.flush();
        assertThat(usage.hasReferences(owner.getId(),origin)).isFalse();
        assertThat(bankService.delete(actor.getId(),owner.getId(),origin,1).action()).isEqualTo(BankAccountService.DeleteAction.DELETED);
    }
    @Test void EqualIdsInDifferentEntityTypesMustNotTurnAnUnusedBankIntoAUsedOne() {
        jdbc.update("UPDATE financial_bank_transfers SET id=? WHERE id=?",spare,transfer);
        assertThat(transfers.findById(spare)).isPresent();assertThat(usage.hasReferences(owner.getId(),spare)).isFalse();
        assertThat(bankService.delete(actor.getId(),owner.getId(),spare,0).action()).isEqualTo(BankAccountService.DeleteAction.DELETED);
        assertThat(transfers.findById(spare)).isPresent();
    }
    @Test void scalarReferenceProbeMustReadCurrentDatabaseRatherThanManagedOldState() {
        new TransactionTemplate(transactions).executeWithoutResult(status->{
            var old=transfers.findById(transfer).orElseThrow();
            jdbc.update("UPDATE financial_bank_transfers SET destination_bank_account_id=?,version=version+1 WHERE id=?",spare,transfer);
            assertThat(old.getValues().destinationBankAccountId()).isEqualTo(destination);
            assertThat(usage.hasReferences(owner.getId(),destination)).isFalse();assertThat(usage.hasReferences(owner.getId(),spare)).isTrue();
        });
    }
    @Test void staleDetachedDeleteMustNotRemoveNewerState() {
        var stale=transfers.findById(transfer).orElseThrow();
        jdbc.update("UPDATE financial_bank_transfers SET version=version+1 WHERE id=?",transfer);
        assertThatThrownBy(()->transfers.delete(stale)).isInstanceOf(org.springframework.orm.ObjectOptimisticLockingFailureException.class);
        assertThat(transfers.findById(transfer).orElseThrow().getVersion()).isEqualTo(1);
    }
    @Test void databaseConstraintsMustProtectAmountDatesDistinctBanksAndActor() {
        for(String assignment:List.of("amount=-1","amount='NaN'::numeric","transfer_date=DATE '10000-01-01'","origin_bank_account_id=destination_bank_account_id","created_by='00000000-0000-0000-0000-000000000001'::uuid")) {
            new TransactionTemplate(transactions).executeWithoutResult(status->{
                assertThatThrownBy(()->jdbc.update("UPDATE financial_bank_transfers SET "+assignment+" WHERE id=?",transfer))
                        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
                status.setRollbackOnly();
            });
        }
    }
    private BankTransferValues values(UUID from,UUID to,BigDecimal amount) {return new BankTransferValues(from,to,LocalDate.of(2025,12,1),amount," Synthetic Transfer ");}
    private UUID bank(Company company,String label) {return banks.saveAndFlush(new BankAccount(company,"Synthetic Transfer "+label,BankAccountType.CASH,new BigDecimal("5.00"),LocalDate.of(2026,1,1),true)).getId();}
    private Company company(String suffix,String cnpj) {
        var firm=firms.saveAndFlush(new AccountingFirm("Synthetic Transfer Firm "+suffix,true));firmIds.add(firm.getId());
        var client=clients.saveAndFlush(new Client(firm,"Synthetic Transfer Client "+suffix,true));clientIds.add(client.getId());
        return companies.saveAndFlush(new Company(client,"Synthetic Transfer Company "+suffix,cnpj));
    }
}
