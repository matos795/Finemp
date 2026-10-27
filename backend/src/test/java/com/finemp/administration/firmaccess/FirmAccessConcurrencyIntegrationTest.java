package com.finemp.administration.firmaccess;

import static org.assertj.core.api.Assertions.assertThat;

import com.finemp.TestcontainersConfiguration;
import com.finemp.access.AccountingFirmAccessGrantRepository;
import com.finemp.access.AccountingFirmPermission;
import com.finemp.access.PlatformPermission;
import com.finemp.access.UserAccount;
import com.finemp.access.UserAccountRepository;
import com.finemp.accountingfirm.AccountingFirm;
import com.finemp.accountingfirm.AccountingFirmRepository;
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

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class FirmAccessConcurrencyIntegrationTest {

    @Autowired private FirmAccessAdministrationService service;
    @Autowired private UserAccountRepository users;
    @Autowired private AccountingFirmRepository firms;
    @Autowired private AccountingFirmAccessGrantRepository grants;
    @Autowired private JdbcTemplate jdbc;
    private UUID actorId;
    private UUID targetId;
    private UUID firmId;

    @AfterEach
    void cleanupOwnSyntheticRows() {
        jdbc.update("DELETE FROM access_audit_events WHERE subject_user_account_id = ?", targetId);
        jdbc.update("DELETE FROM accounting_firm_access_grant_permissions WHERE accounting_firm_access_grant_id IN"
                + " (SELECT id FROM accounting_firm_access_grants WHERE accounting_firm_id = ?)", firmId);
        jdbc.update("DELETE FROM accounting_firm_access_grants WHERE accounting_firm_id = ?", firmId);
        jdbc.update("DELETE FROM accounting_firms WHERE id = ?", firmId);
        jdbc.update("DELETE FROM user_account_platform_permissions WHERE user_account_id IN (?, ?)", actorId, targetId);
        jdbc.update("DELETE FROM user_accounts WHERE id IN (?, ?)", actorId, targetId);
    }

    @Test
    void shouldSerializeConcurrentCreationIntoOneBindingAndTwoOrderedMutations() throws Exception {
        actorId = users.saveAndFlush(new UserAccount("Synthetic Root", "firm-root-" + UUID.randomUUID()
                + "@example.invalid", "hash", Set.of(PlatformPermission.PLATFORM_USER_MANAGE))).getId();
        targetId = users.saveAndFlush(new UserAccount("Synthetic Target", "firm-target-" + UUID.randomUUID()
                + "@example.invalid", "hash")).getId();
        firmId = firms.saveAndFlush(new AccountingFirm("Synthetic Concurrent Firm", true)).getId();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var tasks = List.of(AccountingFirmPermission.CONNECTOR_MANAGE, AccountingFirmPermission.HIERARCHY_MANAGE)
                    .stream().<java.util.concurrent.Callable<Boolean>>map(permission -> () -> {
                        ready.countDown();
                        if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Test did not start.");
                        service.replace(actorId, firmId, targetId, Set.of(permission));
                        return true;
                    }).toList();
            var first = executor.submit(tasks.get(0));
            var second = executor.submit(tasks.get(1));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(first.get(15, TimeUnit.SECONDS)).isTrue();
            assertThat(second.get(15, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM accounting_firm_access_grants"
                + " WHERE user_account_id = ? AND accounting_firm_id = ?", Long.class, targetId, firmId)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM access_audit_events WHERE subject_user_account_id = ?",
                Long.class, targetId)).isEqualTo(2L);
    }
}
