package com.finemp.administration;

import static org.assertj.core.api.Assertions.assertThat;

import com.finemp.TestcontainersConfiguration;
import com.finemp.accounting.source.AccountingSource;
import com.finemp.accounting.source.AccountingSourceRepository;
import com.finemp.accounting.source.AccountingSourceType;
import com.finemp.company.Company;
import com.finemp.company.CompanyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class AccountingSourceAdministrationQueryIntegrationTest {

    @Autowired private AccountingSourceRepository sources;
    @Autowired private CompanyRepository companies;

    @Test
    void shouldSearchAndPageWithinTheCompanyIncludingNullReferencesForEmptySearch() {
        var company = companies.saveAndFlush(new Company("Synthetic Search", "33333333000133"));
        var other = companies.saveAndFlush(new Company("Synthetic Other", "44444444000144"));
        var monthly = sources.saveAndFlush(new AccountingSource(company, AccountingSourceType.QUESTOR, "Monthly ledger", true));
        sources.saveAndFlush(new AccountingSource(company, AccountingSourceType.QUESTOR, "Annual ledger", true));
        sources.saveAndFlush(new AccountingSource(company, AccountingSourceType.QUESTOR, null, true));
        sources.saveAndFlush(new AccountingSource(other, AccountingSourceType.QUESTOR, "Monthly foreign", true));

        var page = sources.searchByCompany(company.getId(), "", PageRequest.of(0, 1));
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).hasSize(1);
        var result = sources.searchByCompany(company.getId(), "MONTHLY", PageRequest.of(0, 10));
        assertThat(result.getContent()).extracting(AccountingSource::getId).containsExactly(monthly.getId());
        assertThat(result.getTotalElements()).isEqualTo(1);
    }
}
