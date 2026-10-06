package com.finemp.company;

import static org.assertj.core.api.Assertions.assertThat;

import com.finemp.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class CompanyRepositoryIntegrationTest {

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void shouldPersistAndRetrieveCompany() {
        Company company = new Company(
                "Empresa Exemplo Ltda",
                "12345678000190"
        );

        Company savedCompany = companyRepository.save(company);

        entityManager.flush();
        entityManager.clear();

        Company retrievedCompany = companyRepository
                .findById(savedCompany.getId())
                .orElseThrow();

        assertThat(retrievedCompany.getLegalName())
                .isEqualTo("Empresa Exemplo Ltda");

        assertThat(retrievedCompany.getCnpj())
                .isEqualTo("12345678000190");
    }
}