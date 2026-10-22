package com.finemp.access;

import com.finemp.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class UserAccountRepositoryIntegrationTest {

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Test
    void shouldRejectDuplicateEmailIgnoringCase() {

        userAccountRepository.saveAndFlush(
                new UserAccount(
                        "Usuário A",
                        "usuario@example.com",
                        "hash-exemplo-a"));

        assertThatThrownBy(() -> userAccountRepository.saveAndFlush(
                new UserAccount(
                        "Usuário B",
                        "USUARIO@example.com",
                        "hash-exemplo-b")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}