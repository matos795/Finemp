package com.finemp.administration.companyaccess;

import static org.assertj.core.api.Assertions.assertThat;

import com.finemp.TestcontainersConfiguration;
import com.finemp.access.*;
import com.finemp.accountingfirm.*;
import com.finemp.administration.firmaccess.FirmAccessAdministrationService;
import com.finemp.administration.firmaccess.FirmAccessConflictException;
import com.finemp.client.*;
import com.finemp.company.*;
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
class CompanyAccessConcurrencyIntegrationTest {
    @Autowired private CompanyAccessAdministrationService service;
    @Autowired private FirmAccessAdministrationService firmService;
    @Autowired private UserAccountRepository users;
    @Autowired private AccountingFirmRepository firms;
    @Autowired private AccountingFirmAccessGrantRepository firmGrants;
    @Autowired private ClientRepository clients;
    @Autowired private CompanyRepository companies;
    @Autowired private JdbcTemplate jdbc;
    private UUID actor;
    private UUID target;
    private UUID firm;
    private UUID client;
    private UUID company;

    @AfterEach
    void cleanOnlyOwnSyntheticRows() {
        jdbc.update("DELETE FROM access_audit_events WHERE subject_user_account_id = ?", target);
        jdbc.update("DELETE FROM company_access_grant_permissions WHERE company_access_grant_id IN (SELECT id FROM company_access_grants WHERE company_id = ?)", company);
        jdbc.update("DELETE FROM company_access_grants WHERE company_id = ?", company);
        jdbc.update("DELETE FROM companies WHERE id = ?", company);
        jdbc.update("DELETE FROM clients WHERE id = ?", client);
        jdbc.update("DELETE FROM accounting_firm_access_grant_permissions WHERE accounting_firm_access_grant_id IN (SELECT id FROM accounting_firm_access_grants WHERE accounting_firm_id = ?)", firm);
        jdbc.update("DELETE FROM accounting_firm_access_grants WHERE accounting_firm_id = ?", firm);
        jdbc.update("DELETE FROM accounting_firms WHERE id = ?", firm);
        jdbc.update("DELETE FROM user_account_platform_permissions WHERE user_account_id IN (?, ?)", actor, target);
        jdbc.update("DELETE FROM user_accounts WHERE id IN (?, ?)", actor, target);
    }

    @Test
    void shouldNeverLeaveNewCompanyAccessWithoutTheFirmMembershipDuringConcurrentRevocation() throws Exception {
        var root = users.saveAndFlush(new UserAccount("Synthetic Root", UUID.randomUUID() + "@example.invalid", "hash",
                Set.of(PlatformPermission.PLATFORM_USER_MANAGE)));
        var member = users.saveAndFlush(new UserAccount("Synthetic Member", UUID.randomUUID() + "@example.invalid", "hash"));
        var ownedFirm = firms.saveAndFlush(new AccountingFirm("Synthetic Concurrent", true));
        var ownedClient = clients.saveAndFlush(new Client(ownedFirm, "Synthetic", true));
        var ownedCompany = companies.saveAndFlush(new Company(ownedClient, "Synthetic", "18181818000118"));
        actor = root.getId(); target = member.getId(); firm = ownedFirm.getId(); client = ownedClient.getId(); company = ownedCompany.getId();
        firmGrants.saveAndFlush(new AccountingFirmAccessGrant(member, ownedFirm, Set.of()));
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var granting = executor.submit(() -> {
                await(ready, start);
                try {
                    service.replace(actor, firm, client, company, target,
                            new SetCompanyAccessRequest(CompanyAccessRole.VIEWER, CostCenterScope.ALL, Set.of(CompanyPermission.DRE_VIEW)));
                    return true;
                } catch (CompanyAccessConflictException expected) { return false; }
            });
            var revoking = executor.submit(() -> {
                await(ready, start);
                try { firmService.revoke(actor, firm, target); return true; }
                catch (FirmAccessConflictException expected) { return false; }
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            boolean granted = granting.get(15, TimeUnit.SECONDS);
            boolean revoked = revoking.get(15, TimeUnit.SECONDS);
            assertThat(granted).isNotEqualTo(revoked);
            long companyBindings = jdbc.queryForObject("SELECT COUNT(*) FROM company_access_grants WHERE user_account_id = ? AND company_id = ?", Long.class, target, company);
            long firmBindings = jdbc.queryForObject("SELECT COUNT(*) FROM accounting_firm_access_grants WHERE user_account_id = ? AND accounting_firm_id = ?", Long.class, target, firm);
            assertThat(companyBindings).isEqualTo(granted ? 1L : 0L);
            assertThat(firmBindings).isEqualTo(granted ? 1L : 0L);
        }
    }

    private void await(CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Test did not start.");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted synthetic test.", interrupted);
        }
    }
}
