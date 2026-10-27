package com.finemp.administration;

import static org.assertj.core.api.Assertions.assertThat;

import com.finemp.TestcontainersConfiguration;
import com.finemp.access.AccountingFirmAccessGrant;
import com.finemp.access.AccountingFirmAccessGrantRepository;
import com.finemp.access.AccountingFirmPermission;
import com.finemp.access.PlatformPermission;
import com.finemp.access.UserAccount;
import com.finemp.access.UserAccountRepository;
import com.finemp.accountingfirm.AccountingFirm;
import com.finemp.accountingfirm.AccountingFirmRepository;
import com.finemp.client.Client;
import com.finemp.client.ClientRepository;
import com.finemp.company.Company;
import com.finemp.company.CompanyRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CompanyAssignmentConcurrencyIntegrationTest {

    @Autowired private SaasAdministrationService service;
    @Autowired private UserAccountRepository users;
    @Autowired private AccountingFirmRepository firms;
    @Autowired private AccountingFirmAccessGrantRepository grants;
    @Autowired private ClientRepository clients;
    @Autowired private CompanyRepository companies;
    @Autowired private JdbcTemplate jdbc;

    private UUID firmId;
    private UUID actorId;
    private UUID grantId;
    private UUID firstClientId;
    private UUID secondClientId;
    private UUID companyId;

    @AfterEach
    void deleteOnlyThisTestsSyntheticRows() {
        jdbc.update("DELETE FROM company_client_assignment_events WHERE company_id = ?", companyId);
        jdbc.update("DELETE FROM companies WHERE id = ?", companyId);
        jdbc.update("DELETE FROM clients WHERE id IN (?, ?)", firstClientId, secondClientId);
        jdbc.update("DELETE FROM accounting_firm_access_grant_permissions WHERE accounting_firm_access_grant_id = ?", grantId);
        jdbc.update("DELETE FROM accounting_firm_access_grants WHERE id = ?", grantId);
        jdbc.update("DELETE FROM user_account_platform_permissions WHERE user_account_id = ?", actorId);
        jdbc.update("DELETE FROM user_accounts WHERE id = ?", actorId);
        jdbc.update("DELETE FROM accounting_firms WHERE id = ?", firmId);
    }

    @Test
    void shouldAssignAnUnlinkedCompanyToOnlyOneClientUnderConcurrentRequests() throws Exception {
        var firm = firms.saveAndFlush(new AccountingFirm("Synthetic Assignment", true));
        firmId = firm.getId();
        var actor = users.saveAndFlush(new UserAccount("Synthetic Assignment Admin",
                "assignment-" + UUID.randomUUID() + "@example.invalid", "synthetic-hash",
                Set.of(PlatformPermission.UNASSIGNED_COMPANY_ASSIGN)));
        actorId = actor.getId();
        grantId = grants.saveAndFlush(new AccountingFirmAccessGrant(actor, firm,
                Set.of(AccountingFirmPermission.HIERARCHY_MANAGE))).getId();
        firstClientId = clients.saveAndFlush(new Client(firm, "Synthetic First", true)).getId();
        secondClientId = clients.saveAndFlush(new Client(firm, "Synthetic Second", true)).getId();
        String syntheticCnpj = Long.toString(ThreadLocalRandom.current().nextLong(10_000_000_000_000L, 99_999_999_999_999L));
        companyId = companies.saveAndFlush(new Company("Synthetic Unlinked", syntheticCnpj)).getId();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var tasks = List.of(firstClientId, secondClientId).stream()
                    .<java.util.concurrent.Callable<Boolean>>map(clientId -> () -> {
                        ready.countDown();
                        if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Test did not start.");
                        try {
                            service.assignExistingCompany(actorId, firmId, clientId, companyId);
                            return true;
                        } catch (CompanyAssignmentConflictException expected) {
                            return false;
                        }
                    }).toList();
            var first = executor.submit(tasks.get(0));
            var second = executor.submit(tasks.get(1));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(jdbc.queryForObject("SELECT client_id FROM companies WHERE id = ?", UUID.class, companyId))
                .isIn(firstClientId, secondClientId);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM company_client_assignment_events WHERE company_id = ?",
                Long.class, companyId)).isEqualTo(1L);
    }
}
