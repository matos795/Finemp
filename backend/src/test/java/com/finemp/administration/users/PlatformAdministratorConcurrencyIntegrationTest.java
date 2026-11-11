package com.finemp.administration.users;

import static org.assertj.core.api.Assertions.assertThat;

import com.finemp.TestcontainersConfiguration;
import com.finemp.access.PlatformPermission;
import com.finemp.access.UserAccount;
import com.finemp.access.UserAccountRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PlatformAdministratorConcurrencyIntegrationTest {

    @Autowired private PlatformUserAdministrationService service;
    @Autowired private UserAccountRepository users;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactions;
    private UUID first;
    private UUID second;

    @AfterEach
    void cleanupOnlyOwnSyntheticRows() {
        jdbc.update("DELETE FROM access_audit_events WHERE subject_user_account_id IN (?, ?)", first, second);
        jdbc.update("DELETE FROM user_account_platform_permissions WHERE user_account_id IN (?, ?)", first, second);
        jdbc.update("DELETE FROM user_accounts WHERE id IN (?, ?)", first, second);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = PlatformPermission.class, names = {"PLATFORM_USER_MANAGE", "SYSTEM_ADMIN"})
    void shouldNeverRemoveBothLastAdministratorsAcrossConcurrentTransactions(PlatformPermission permission) throws Exception {
        first = admin(permission);
        second = admin(permission);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var tasks = List.of(first, second).stream().<java.util.concurrent.Callable<Boolean>>map(id -> () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Test did not start.");
                try {
                    service.replacePermissions(id, id, Set.of());
                    return true;
                } catch (PlatformUserConflictException expected) {
                    return false;
                }
            }).toList();
            var firstResult = executor.submit(tasks.get(0));
            var secondResult = executor.submit(tasks.get(1));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(firstResult.get(15, TimeUnit.SECONDS), secondResult.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(users.countActiveByPlatformPermission(permission)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM access_audit_events WHERE subject_user_account_id IN (?, ?)",
                Long.class, first, second)).isEqualTo(1L);
    }

    @Test
    void shouldRejectAnOuterTransactionWithAnIncompatibleSnapshotInsteadOfUsingStaleCounts() {
        first = admin(PlatformPermission.PLATFORM_USER_MANAGE);
        var transaction = new TransactionTemplate(transactions);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);

        assertThatThrownBy(() -> transaction.executeWithoutResult(status ->
                service.replacePermissions(first, first, Set.of())))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("READ_COMMITTED");

        assertThat(users.existsByIdAndPlatformPermission(first, PlatformPermission.PLATFORM_USER_MANAGE)).isTrue();
    }

    @Test
    void normalManagerCannotManageGlobalIdentityOrPromoteItself() {
        first = admin(PlatformPermission.PLATFORM_USER_MANAGE);
        second = admin(PlatformPermission.SYSTEM_ADMIN);
        assertThatThrownBy(() -> service.replacePermissions(first, first, Set.of(PlatformPermission.SYSTEM_ADMIN)))
                .isInstanceOf(com.finemp.administration.OrganizationAccessDeniedException.class);
        assertThatThrownBy(() -> service.replacePermissions(first, second, Set.of()))
                .isInstanceOf(com.finemp.administration.OrganizationAccessDeniedException.class);
        assertThatThrownBy(() -> service.setActive(first, second, false))
                .isInstanceOf(com.finemp.administration.OrganizationAccessDeniedException.class);
        assertThatThrownBy(() -> service.setActive(second, second, false))
                .isInstanceOf(PlatformUserConflictException.class);
        service.replacePermissions(second, first, Set.of(PlatformPermission.SYSTEM_ADMIN, PlatformPermission.PLATFORM_USER_MANAGE));
        service.replacePermissions(first, second, Set.of());
        assertThat(users.countActiveByPlatformPermission(PlatformPermission.SYSTEM_ADMIN)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM access_audit_events WHERE subject_user_account_id IN (?,?)",
                Long.class, first, second)).isEqualTo(2);
    }

    @Test
    void globalDelegationKeepsUserAdministrationAvailableWithoutRedundantPermissions() {
        first = admin(PlatformPermission.SYSTEM_ADMIN);
        second = admin(PlatformPermission.PLATFORM_USER_MANAGE);
        service.replacePermissions(first, first, Set.of(PlatformPermission.SYSTEM_ADMIN, PlatformPermission.PLATFORM_USER_MANAGE));
        service.replacePermissions(first, second, Set.of(PlatformPermission.SYSTEM_ADMIN));
        service.replacePermissions(first, first, Set.of(PlatformPermission.SYSTEM_ADMIN));
        service.setActive(second, first, false);
        assertThat(users.countActiveByPlatformPermission(PlatformPermission.SYSTEM_ADMIN)).isEqualTo(1);
    }

    private UUID admin(PlatformPermission permission) {
        return users.saveAndFlush(new UserAccount("Synthetic Concurrent Admin", "platform-concurrent-" + UUID.randomUUID()
                + "@example.invalid", "synthetic-hash", Set.of(permission))).getId();
    }
}
