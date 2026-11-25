package com.finemp.accounting.source;

import static org.assertj.core.api.Assertions.assertThat;

import com.finemp.TestcontainersConfiguration;
import com.finemp.company.Company;
import com.finemp.company.CompanyRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class AccountingSourceRepositoryIntegrationTest {

    @Autowired
    private AccountingSourceRepository accountingSourceRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void shouldPersistAccountingSourceForCompany() {
        Company company = companyRepository.save(new Company("Empresa Exemplo Ltda", "12345678000190"));

        AccountingSource source = new AccountingSource(
                company,
                AccountingSourceType.QUESTOR,
                "57",
                true);

        AccountingSource savedSource = accountingSourceRepository.save(source);

        entityManager.flush();
        entityManager.clear();

        AccountingSource retrieved = accountingSourceRepository.findById(savedSource.getId()).orElseThrow();

        assertThat(retrieved.getType()).isEqualTo(AccountingSourceType.QUESTOR);
        assertThat(retrieved.getExternalReference()).isEqualTo("57");
        assertThat(retrieved.isActive()).isTrue();
        assertThat(retrieved.getCompany().getId()).isEqualTo(company.getId());
    }

    @Test
    void shouldPreserveConnectorRouteWhenUpdatingExternalReference() {
        Company company = companyRepository.save(
                new Company("Synthetic Connector Company", "66666666000166"));

        AccountingSource source = accountingSourceRepository.save(
                new AccountingSource(
                        company,
                        AccountingSourceType.QUESTOR,
                        null,
                        true));

        source.reserveConnectorRoute();
        source.updateExternalReference("57");

        entityManager.flush();
        entityManager.clear();

        AccountingSource retrieved = accountingSourceRepository
                .findById(source.getId())
                .orElseThrow();

        assertThat(retrieved.getId()).isEqualTo(source.getId());
        assertThat(retrieved.getExternalReference()).isEqualTo("57");
        assertThat(retrieved.getConnectionMode())
                .isEqualTo(AccountingConnectionMode.CONNECTOR);
    }

    @Test
    void shouldPersistFileImportSourceAsUploadWithoutExternalReference() {
        Company company = companyRepository.save(
                new Company("Synthetic File Import", "76767676000176"));

        AccountingSource source = accountingSourceRepository.save(
                new AccountingSource(
                        company,
                        AccountingSourceType.FILE_IMPORT,
                        null,
                        true));

        entityManager.flush();
        entityManager.clear();

        AccountingSource retrieved = accountingSourceRepository
                .findById(source.getId())
                .orElseThrow();

        assertThat(retrieved.getType()).isEqualTo(AccountingSourceType.FILE_IMPORT);
        assertThat(retrieved.getConnectionMode()).isEqualTo(AccountingConnectionMode.UPLOAD);
        assertThat(retrieved.getExternalReference()).isNull();
    }

    @Test
    void shouldRejectExternalReferenceAndConnectorRouteForFileImportSource() {
        Company company = companyRepository.save(
                new Company("Synthetic File Rules", "77777777000177"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                new AccountingSource(
                        company,
                        AccountingSourceType.FILE_IMPORT,
                        "not-applicable",
                        true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("File import source must not have an external reference.");

        AccountingSource source = new AccountingSource(
                company,
                AccountingSourceType.FILE_IMPORT,
                null,
                true);

        org.assertj.core.api.Assertions.assertThatThrownBy(source::reserveConnectorRoute)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Only Questor sources can use the Connector route.");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> source.updateExternalReference("57"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("File import source has no external reference.");
    }

    @Test
    void shouldAllowMultipleAccountingSourcesForSameCompany() {
        Company company = companyRepository.save(
                new Company("Empresa Exemplo Ltda", "12345678000190"));

        accountingSourceRepository.save(
                new AccountingSource(
                        company,
                        AccountingSourceType.QUESTOR,
                        "57",
                        true));

        accountingSourceRepository.save(
                new AccountingSource(
                        company,
                        AccountingSourceType.QUESTOR,
                        "58",
                        false));

        entityManager.flush();

        assertThat(accountingSourceRepository.count()).isEqualTo(2);
    }
}