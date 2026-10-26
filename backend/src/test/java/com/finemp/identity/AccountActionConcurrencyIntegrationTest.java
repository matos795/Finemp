package com.finemp.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.finemp.TestcontainersConfiguration;
import com.finemp.access.UserAccountRepository;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;

@SpringBootTest
@Import({TestcontainersConfiguration.class, AccountActionConcurrencyIntegrationTest.CaptureConfiguration.class})
class AccountActionConcurrencyIntegrationTest {

    @Autowired
    private AccountActionService service;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private Capture capture;

    private String email;

    @AfterEach
    void cleanOwnSyntheticAccount() {
        if (email != null) {
            userAccountRepository.findByEmailIgnoreCase(email)
                    .ifPresent(user -> userAccountRepository.deleteById(user.getId()));
        }
    }

    @Test
    void shouldConsumeAnInvitationOnlyOnceAcrossConcurrentTransactions() throws Exception {
        email = "concurrent-" + UUID.randomUUID() + "@example.invalid";
        service.inviteNewUser("Synthetic Concurrent", email);
        AccountActionRequested event = capture.events.poll(5, TimeUnit.SECONDS);
        assertThat(event).isNotNull();
        String token = event.token().rawValue();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var task = (java.util.concurrent.Callable<Boolean>) () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Concurrent test did not start.");
                }
                try {
                    service.complete(token, AccountActionPurpose.INVITATION, "syntheticPassword123");
                    return true;
                } catch (InvalidAccountActionException expected) {
                    return false;
                }
            };
            var first = executor.submit(task);
            var second = executor.submit(task);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(java.util.List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }

        assertThat(userAccountRepository.findByEmailIgnoreCase(email).orElseThrow().getCredentialsVersion())
                .isEqualTo(1);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class CaptureConfiguration {

        @Bean
        Capture capture() {
            return new Capture();
        }
    }

    static class Capture {

        final LinkedBlockingQueue<AccountActionRequested> events = new LinkedBlockingQueue<>();

        @EventListener
        public void requested(AccountActionRequested event) {
            events.add(event);
        }
    }
}
