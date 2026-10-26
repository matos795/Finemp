package com.finemp.hierarchy;

import static org.assertj.core.api.Assertions.assertThat;

import com.finemp.TestcontainersConfiguration;
import com.finemp.accounting.source.AccountingSource;
import com.finemp.accounting.source.AccountingSourceRepository;
import com.finemp.accounting.source.AccountingSourceType;
import com.finemp.accountingfirm.AccountingFirm;
import com.finemp.accountingfirm.AccountingFirmRepository;
import com.finemp.client.Client;
import com.finemp.client.ClientRepository;
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
class SaasHierarchyRepositoryIntegrationTest {

    @Autowired
    private AccountingFirmRepository accountingFirmRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private AccountingSourceRepository accountingSourceRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void shouldPersistTheCompleteSaasHierarchy() {
        AccountingFirm accountingFirm = accountingFirmRepository.save(
                new AccountingFirm("Contabilidade Exemplo", true));

        Client client = clientRepository.save(
                new Client(accountingFirm, "Grupo Exemplo", true));

        Company company = companyRepository.save(
                new Company(client, "Empresa Exemplo Ltda", "12345678000190"));

        AccountingSource source = accountingSourceRepository.save(
                new AccountingSource(
                        company,
                        AccountingSourceType.QUESTOR,
                        "empresa-externa-1",
                        true));

        entityManager.flush();
        entityManager.clear();

        Client persistedClient = clientRepository.findById(client.getId()).orElseThrow();
        Company persistedCompany = companyRepository.findById(company.getId()).orElseThrow();
        AccountingSource persistedSource = accountingSourceRepository.findById(source.getId()).orElseThrow();

        assertThat(persistedClient.getAccountingFirm().getId()).isEqualTo(accountingFirm.getId());
        assertThat(persistedCompany.getClient().getId()).isEqualTo(client.getId());
        assertThat(persistedSource.getCompany().getId()).isEqualTo(company.getId());
    }

    @Test
    void shouldKeepClientsAndCompaniesIsolatedByTheirParents() {
        AccountingFirm firstFirm = accountingFirmRepository.save(
                new AccountingFirm("Contabilidade Um", true));
        AccountingFirm secondFirm = accountingFirmRepository.save(
                new AccountingFirm("Contabilidade Dois", true));

        Client firstClient = clientRepository.save(
                new Client(firstFirm, "Cliente com nome repetido", true));
        Client secondClient = clientRepository.save(
                new Client(secondFirm, "Cliente com nome repetido", true));

        Company firstCompany = companyRepository.save(
                new Company(firstClient, "Empresa Um", "11111111000191"));
        Company secondCompany = companyRepository.save(
                new Company(secondClient, "Empresa Dois", "22222222000191"));

        entityManager.flush();

        assertThat(clientRepository.findAllByAccountingFirmId(firstFirm.getId()))
                .extracting(Client::getId)
                .containsExactly(firstClient.getId());

        assertThat(companyRepository.findAllByClientId(firstClient.getId()))
                .extracting(Company::getId)
                .containsExactly(firstCompany.getId())
                .doesNotContain(secondCompany.getId());
    }

    @Test
    void shouldPreserveAnExistingCompanyWithoutInventingAClientRelationship() {
        Company legacyCompany = companyRepository.save(
                new Company("Empresa anterior à hierarquia", "33333333000191"));

        entityManager.flush();
        entityManager.clear();

        Company persistedCompany = companyRepository.findById(legacyCompany.getId()).orElseThrow();

        assertThat(persistedCompany.getClient()).isNull();
    }
}

