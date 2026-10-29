package com.finemp.finance.withholding;

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

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class FinancialWithholdingPersistenceIntegrationTest {
    @Autowired FinancialWithholdingRepository withholdings;@Autowired FinancialEntryRepository entries;
    @Autowired BankAccountRepository banks;@Autowired FinancialCategoryRepository categories;@Autowired CompanyRepository companies;@Autowired ClientRepository clients;@Autowired AccountingFirmRepository firms;@Autowired UserAccountRepository users;
    @Autowired JdbcTemplate jdbc;@Autowired PlatformTransactionManager transactions;
    Company owner,other;UserAccount actor;FinancialEntry entry,foreign;final List<UUID> firmIds=new ArrayList<>(),clientIds=new ArrayList<>();
    @BeforeEach void prepare() {
        owner=company("Owner","10101010000110");other=company("Other","20202020000120");actor=users.saveAndFlush(new UserAccount("Synthetic Withholding Actor",UUID.randomUUID()+"@example.invalid","hash"));
        entry=entry(owner);foreign=entry(other);
    }
    @AfterEach void cleanup() {
        for(var company:List.of(owner,other)) {
            for(var table:List.of("financial_tax_withholdings","financial_entries","financial_bank_accounts","financial_categories"))jdbc.update("DELETE FROM "+table+" WHERE company_id=?",company.getId());
            companies.deleteById(company.getId());
        }
        for(var id:clientIds)clients.deleteById(id);for(var id:firmIds)firms.deleteById(id);users.deleteById(actor.getId());
    }
    @Test void exactAmountsAndExplicitTreatmentsMustPersistWithoutChangingTheLinkedEntry() {
        for(var treatment:WithholdingTreatment.values()) {
            var row=withholdings.saveAndFlush(new FinancialWithholding(owner,entry,new WithholdingValues("IRRF",treatment,new BigDecimal("99999999999999999.99"),"Documento informado"),actor.getId()));
            var loaded=withholdings.findByIdAndCompanyId(row.getId(),owner.getId()).orElseThrow();
            assertThat(loaded.getValues().amount()).isEqualTo(new BigDecimal("99999999999999999.99"));assertThat(loaded.getValues().treatment()).isEqualTo(treatment);assertThat(loaded.getEntryId()).isEqualTo(entry.getId());
            assertThat(loaded.getCreatedBy()).isEqualTo(actor.getId());assertThat(loaded.getUpdatedBy()).isEqualTo(actor.getId());assertThat(loaded.getCreatedAt()).isNotNull();assertThat(loaded.getVersion()).isZero();
        }
        assertThat(entries.findById(entry.getId()).orElseThrow().getValues()).isEqualTo(entry.getValues());assertThat(entry.getValues().nominalAmount()).isEqualByComparingTo("1");
    }
    @Test void constructorAndPhysicalCompositeReferenceMustRejectAnotherCompanysEntry() {
        assertThatThrownBy(()->new FinancialWithholding(owner,foreign,values("1"),actor.getId())).isInstanceOf(IllegalArgumentException.class);
        var row=save();
        assertThatThrownBy(()->new TransactionTemplate(transactions).execute(status->jdbc.update("UPDATE financial_tax_withholdings SET entry_id=? WHERE company_id=? AND id=?",foreign.getId(),owner.getId(),row.getId())))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(withholdings.findById(row.getId()).orElseThrow().getEntryId()).isEqualTo(entry.getId());
    }
    @Test void scopedQueriesAndPagingMustNeverReturnAnotherCompanysWithholding() {
        var own=save();var theirs=withholdings.saveAndFlush(new FinancialWithholding(other,foreign,values("2"),actor.getId()));
        assertThat(withholdings.findByIdAndCompanyId(theirs.getId(),owner.getId())).isEmpty();
        var page=withholdings.findByCompanyIdAndEntryId(owner.getId(),entry.getId(),PageRequest.of(0,1,Sort.by("id")));
        assertThat(page.getTotalElements()).isEqualTo(1);assertThat(page.getContent()).extracting(FinancialWithholding::getId).containsExactly(own.getId());
        assertThat(withholdings.findByCompanyIdAndEntryId(owner.getId(),foreign.getId(),PageRequest.of(0,10))).isEmpty();
    }
    @Test void staleUpdatesMustNotOverwriteMoreRecentValuesOrActorTrace() {
        var row=save();var stale=withholdings.findById(row.getId()).orElseThrow();var current=withholdings.findById(row.getId()).orElseThrow();
        current.replace(new WithholdingValues("ISS",WithholdingTreatment.RECOVERABLE,new BigDecimal("2.34"),"Atualizado"),actor.getId());withholdings.saveAndFlush(current);
        stale.replace(values("9.99"),actor.getId());assertThatThrownBy(()->withholdings.saveAndFlush(stale)).isInstanceOf(OptimisticLockingFailureException.class);
        var loaded=withholdings.findById(row.getId()).orElseThrow();assertThat(loaded.getVersion()).isEqualTo(1);assertThat(loaded.getValues().amount()).isEqualTo(new BigDecimal("2.34"));assertThat(loaded.getValues().taxCode()).isEqualTo("ISS");
    }
    @Test void deletingTheEntryMustBeRestrictedWhileAWithholdingExistsEvenWithCaptureDisabled() {
        var row=save();assertThatThrownBy(()->entries.deleteById(entry.getId())).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(entries.findById(entry.getId())).isPresent();assertThat(withholdings.findById(row.getId())).isPresent();
        withholdings.deleteById(row.getId());entries.deleteById(entry.getId());assertThat(entries.findById(entry.getId())).isEmpty();
    }
    @Test void storageChecksMustRejectNegativeNaNUnknownTreatmentEmptyCodeAndNegativeVersion() {
        var row=save();
        for(var clause:List.of("amount=-0.01","amount='NaN'","treatment='AUTOMATIC'","tax_code=' '","version=-1"))
            assertThatThrownBy(()->new TransactionTemplate(transactions).execute(status->jdbc.update("UPDATE financial_tax_withholdings SET "+clause+" WHERE id=?",row.getId()))).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(withholdings.findById(row.getId()).orElseThrow().getValues().amount()).isEqualByComparingTo("1.23");
    }
    FinancialWithholding save() {return withholdings.saveAndFlush(new FinancialWithholding(owner,entry,values("1.23"),actor.getId()));}
    WithholdingValues values(String amount) {return new WithholdingValues("IRRF",WithholdingTreatment.PAYABLE,new BigDecimal(amount),"");}
    FinancialEntry entry(Company company) {
        var date=LocalDate.of(2026,7,1);var bank=banks.saveAndFlush(new BankAccount(company,"Synthetic Withholding Bank",BankAccountType.CASH,BigDecimal.ZERO,date,true));var group=categories.saveAndFlush(FinancialCategory.group(company,FinancialDirection.EXPENSE,1,"Synthetic Withholding Group"));var category=categories.saveAndFlush(FinancialCategory.subcategory(group,1,"Synthetic Withholding Category"));
        return entries.saveAndFlush(new FinancialEntry(company,new FinancialEntryValues(FinancialDirection.EXPENSE,"Synthetic withholding entry",BigDecimal.ONE,date,date,YearMonth.of(2026,7),date,null,null,bank.getId(),category.getId(),null,null,null,"","Documento original",""),actor.getId()));
    }
    Company company(String name,String cnpj) {var firm=firms.saveAndFlush(new AccountingFirm("Synthetic Withholding Firm "+name,true));firmIds.add(firm.getId());var client=clients.saveAndFlush(new Client(firm,"Synthetic Withholding Client "+name,true));clientIds.add(client.getId());return companies.saveAndFlush(new Company(client,"Synthetic Withholding Company "+name,cnpj));}
}
