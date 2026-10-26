package com.finemp.identity.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.finemp.TestcontainersConfiguration;
import com.finemp.access.UserAccountRepository;
import com.finemp.identity.AccountActionService;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "finemp.identity.mail.enabled=true",
        "finemp.identity.mail.from=sender@example.invalid",
        "finemp.identity.mail.frontend-base-uri=https://app.example.invalid"
})
class AccountActionMailTransactionIntegrationTest {

    @Autowired
    private AccountActionService service;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private AccountActionMailSender sender;

    private String email;

    @AfterEach
    void cleanOwnSyntheticAccount() {
        if (email != null) {
            userAccountRepository.findByEmailIgnoreCase(email)
                    .ifPresent(user -> userAccountRepository.deleteById(user.getId()));
        }
    }

    @Test
    void shouldDeliverOnlyAfterTheAccountAndHashHaveCommitted() {
        email = "commit-" + UUID.randomUUID() + "@example.invalid";
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            service.inviteNewUser("Synthetic Commit", email);
            verifyNoInteractions(sender);
        });

        verify(sender).send(any(AccountActionMail.class));
        assertThat(userAccountRepository.findByEmailIgnoreCase(email)).isPresent();
    }

    @Test
    void shouldNotSendAnUncommittedInvitation() {
        email = "rollback-" + UUID.randomUUID() + "@example.invalid";
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            service.inviteNewUser("Synthetic Rollback", email);
            status.setRollbackOnly();
        });

        verifyNoInteractions(sender);
        assertThat(userAccountRepository.findByEmailIgnoreCase(email)).isEmpty();
    }
}
