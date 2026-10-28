package com.finemp.finance.entry;

import com.finemp.TestcontainersConfiguration;
import com.finemp.access.*;
import com.finemp.accountingfirm.*;
import com.finemp.client.*;
import com.finemp.company.*;
import com.finemp.finance.bank.*;
import com.finemp.finance.category.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties="finemp.finance.entries.enabled=true")
@Import(TestcontainersConfiguration.class)
class EntrySeriesPersistenceIntegrationTest {
    @Autowired FinancialEntrySeriesRepository series;@Autowired EntrySeriesMembershipRepository members;
    @Autowired FinancialEntryRepository entries;@Autowired EntryDeletionCommandService deletion;@Autowired BankAccountRepository banks;@Autowired FinancialCategoryRepository categories;
    @Autowired CompanyRepository companies;@Autowired ClientRepository clients;@Autowired AccountingFirmRepository firms;
    @Autowired UserAccountRepository users;@Autowired CompanyAccessGrantRepository grants;@Autowired JdbcTemplate jdbc;@Autowired PlatformTransactionManager transactions;
    Company owner,other;UserAccount actor;FinancialEntrySeries group,foreignGroup;FinancialEntry target,second,foreignEntry;EntrySeriesMembership member;
    final LocalDate date=LocalDate.of(2026,7,1);
    final List<UUID> firmIds=new ArrayList<>(),clientIds=new ArrayList<>();
    int categoryNumber;
    @BeforeEach void prepare() {
        owner=company("Owner","91919191000191");other=company("Other","92929292000192");
        actor=users.saveAndFlush(new UserAccount("Synthetic Series Manager",UUID.randomUUID()+"@example.invalid","hash"));
        grants.saveAndFlush(new CompanyAccessGrant(actor,owner,CompanyAccessRole.CUSTOM,CostCenterScope.ALL,Set.of(CompanyPermission.FINANCIAL_ENTRY_MANAGE)));
        target=entry(owner);second=entry(owner);foreignEntry=entry(other);
        group=series.saveAndFlush(new FinancialEntrySeries(owner,new EntrySeriesDefinition(SeriesMode.MONTHLY,3),actor.getId()));
        foreignGroup=series.saveAndFlush(new FinancialEntrySeries(other,new EntrySeriesDefinition(SeriesMode.INSTALLMENTS,3),actor.getId()));
        member=members.saveAndFlush(new EntrySeriesMembership(owner,group,target,2));
    }
    @AfterEach void cleanSyntheticFixtures() {
        for(var company:List.of(owner,other)) {
            for(var table:List.of("financial_entry_series_members","financial_entry_series","financial_entry_events","financial_entry_command_receipts","financial_entry_command_keys","financial_entries","financial_bank_accounts","financial_categories"))jdbc.update("DELETE FROM "+table+" WHERE company_id=?",company.getId());
            jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id IN (SELECT id FROM company_access_grants WHERE company_id=?)",company.getId());
            jdbc.update("DELETE FROM company_access_grants WHERE company_id=?",company.getId());jdbc.update("DELETE FROM companies WHERE id=?",company.getId());
        }
        for(var id:clientIds)jdbc.update("DELETE FROM clients WHERE id=?",id);for(var id:firmIds)jdbc.update("DELETE FROM accounting_firms WHERE id=?",id);jdbc.update("DELETE FROM user_accounts WHERE id=?",actor.getId());
    }
    @Test void originalModeQuantityPositionAndTraceMustRoundTripIndependentlyOfMoney() {
        var stored=series.findByIdAndCompanyId(group.getId(),owner.getId()).orElseThrow();assertThat(stored.getDefinition()).isEqualTo(new EntrySeriesDefinition(SeriesMode.MONTHLY,3));
        assertThat(stored.getCreatedBy()).isEqualTo(actor.getId());assertThat(stored.getCreatedAt()).isNotNull();assertThat(stored.getVersion()).isZero();
        var membership=members.findByCompanyIdAndEntryId(owner.getId(),target.getId()).orElseThrow();assertThat(membership.getNumber()).isEqualTo(2);assertThat(membership.getQuantity()).isEqualTo(3);
        members.saveAndFlush(new EntrySeriesMembership(owner,group,second,3));members.deleteById(member.getId());members.flush();
        var remaining=members.findByCompanyIdAndSeriesIdOrderByNumber(owner.getId(),group.getId());assertThat(remaining).hasSize(1);assertThat(remaining.getFirst().getNumber()).isEqualTo(3);assertThat(remaining.getFirst().getQuantity()).isEqualTo(3);
        assertThat(entries.findById(target.getId())).isPresent();assertThat(entries.findById(second.getId()).orElseThrow().getValues().nominalAmount()).isEqualByComparingTo("1.23");
    }
    @Test void lookupsMustNotMixCompaniesClientsOrFirms() {
        assertThat(series.findByIdAndCompanyId(group.getId(),other.getId())).isEmpty();assertThat(members.findByCompanyIdAndEntryId(other.getId(),target.getId())).isEmpty();
        assertThat(members.findByCompanyIdAndSeriesIdOrderByNumber(other.getId(),group.getId())).isEmpty();
    }
    @Test void databaseMustRejectCrossCompanySeriesOrEntryEvenIfConstructorIsBypassed() {
        rejectedInsert(foreignGroup.getId(),second.getId(),1,3);rejectedInsert(group.getId(),foreignEntry.getId(),1,3);
    }
    @Test void eachEntryAndOriginalPositionMustBeUniqueWithinTheCompanySeries() {
        rejectedInsert(group.getId(),target.getId(),1,3);rejectedInsert(group.getId(),second.getId(),2,3);
    }
    @Test void databaseMustRejectQuantityMismatchOutOfRangePositionAndUnsupportedMode() {
        rejectedInsert(group.getId(),second.getId(),1,2);rejectedInsert(group.getId(),second.getId(),4,3);
        new TransactionTemplate(transactions).executeWithoutResult(status->{
            assertThatThrownBy(()->jdbc.update("UPDATE financial_entry_series SET mode='SINGLE' WHERE id=?",group.getId())).isInstanceOf(DataIntegrityViolationException.class);status.setRollbackOnly();
        });
    }
    @Test void existingIndividualDeletionAndRawParentDeletesMustNotSilentlyDiscardSeriesMembership() {
        assertThatThrownBy(()->deletion.delete(actor.getId(),owner.getId(),UUID.randomUUID(),target.getId(),0L)).isInstanceOf(DataIntegrityViolationException.class);
        new TransactionTemplate(transactions).executeWithoutResult(status->{
            assertThatThrownBy(()->jdbc.update("DELETE FROM financial_entry_series WHERE id=?",group.getId())).isInstanceOf(DataIntegrityViolationException.class);status.setRollbackOnly();
        });
        assertThat(entries.findById(target.getId())).isPresent();assertThat(members.findById(member.getId())).isPresent();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM financial_entry_events WHERE company_id=?",Long.class,owner.getId())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM financial_entry_command_receipts WHERE company_id=?",Long.class,owner.getId())).isZero();
    }
    @Test void staleDetachedGroupAndMembershipDeletesMustNotRemoveNewerMetadata() {
        var unused=series.saveAndFlush(new FinancialEntrySeries(owner,new EntrySeriesDefinition(SeriesMode.MONTHLY,2),actor.getId()));
        jdbc.update("UPDATE financial_entry_series SET version=version+1 WHERE id=?",unused.getId());
        assertThatThrownBy(()->series.delete(unused)).isInstanceOf(org.springframework.orm.ObjectOptimisticLockingFailureException.class);
        jdbc.update("UPDATE financial_entry_series_members SET version=version+1 WHERE id=?",member.getId());
        assertThatThrownBy(()->members.delete(member)).isInstanceOf(org.springframework.orm.ObjectOptimisticLockingFailureException.class);
        assertThat(series.findById(unused.getId())).isPresent();assertThat(members.findById(member.getId())).isPresent();
    }
    void rejectedInsert(UUID groupId,UUID entryId,int number,int quantity) {
        new TransactionTemplate(transactions).executeWithoutResult(status->{
            assertThatThrownBy(()->jdbc.update("INSERT INTO financial_entry_series_members(id,company_id,series_id,entry_id,occurrence_number,quantity,version) VALUES(?,?,?,?,?,?,0)",UUID.randomUUID(),owner.getId(),groupId,entryId,number,quantity)).isInstanceOf(DataIntegrityViolationException.class);status.setRollbackOnly();
        });
    }
    FinancialEntry entry(Company company) {
        var bank=banks.saveAndFlush(new BankAccount(company,"Synthetic Series Bank",BankAccountType.CASH,BigDecimal.ZERO,date,true));
        var root=categories.saveAndFlush(FinancialCategory.group(company,FinancialDirection.EXPENSE,++categoryNumber,"Synthetic Series Group"));
        var category=categories.saveAndFlush(FinancialCategory.subcategory(root,1,"Synthetic Series Category"));
        return entries.saveAndFlush(new FinancialEntry(company,new FinancialEntryValues(FinancialDirection.EXPENSE,"Synthetic Series Entry",new BigDecimal("1.23"),date,date,YearMonth.of(2026,7),date,null,null,bank.getId(),category.getId(),null,null,null,"","",""),actor.getId()));
    }
    Company company(String label,String cnpj) {var firm=firms.saveAndFlush(new AccountingFirm("Synthetic Series Firm "+label,true));firmIds.add(firm.getId());var client=clients.saveAndFlush(new Client(firm,"Synthetic Series Client "+label,true));clientIds.add(client.getId());return companies.saveAndFlush(new Company(client,"Synthetic Series Company "+label,cnpj));}
}
