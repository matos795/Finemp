package com.finemp.accounting.fileimport;

import static org.assertj.core.api.Assertions.assertThat;

import com.finemp.TestcontainersConfiguration;
import com.finemp.access.CompanyAccessGrant;
import com.finemp.access.CompanyAccessGrantRepository;
import com.finemp.access.CompanyAccessRole;
import com.finemp.access.CompanyPermission;
import com.finemp.access.CostCenterScope;
import com.finemp.access.UserAccountRepository;
import com.finemp.accounting.source.AccountingSourceRepository;
import com.finemp.accounting.source.AccountingSourceType;
import com.finemp.company.Company;
import com.finemp.company.CompanyRepository;
import com.finemp.access.UserAccount;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AccountingFileImportUiSupportConcurrencyIntegrationTest {

    @Autowired private AccountingFileImportUiSupportService support;
    @Autowired private AccountingSourceRepository sources;
    @Autowired private CompanyRepository companies;
    @Autowired private UserAccountRepository users;
    @Autowired private CompanyAccessGrantRepository grants;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void concurrentPreparationReturnsTheSameSingleActiveSource() throws Exception {
        var tx = new TransactionTemplate(transactionManager);
        UUID[] ids = tx.execute(status -> {
            var actor = users.saveAndFlush(new UserAccount(
                    "Synthetic Concurrent File UI",
                    UUID.randomUUID() + "@example.invalid",
                    "hash"));
            var company = companies.saveAndFlush(new Company(
                    "Synthetic Concurrent File UI",
                    "93939393000193"));
            grants.saveAndFlush(new CompanyAccessGrant(
                    actor,
                    company,
                    CompanyAccessRole.CUSTOM,
                    CostCenterScope.ALL,
                    Set.of(CompanyPermission.ACCOUNTING_IMPORT_MANAGE)));
            return new UUID[] {actor.getId(), company.getId()};
        });

        UUID actorId = ids[0];
        UUID companyId = ids[1];
        var start = new CountDownLatch(1);

        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> prepareAfter(start, actorId, companyId));
            var second = pool.submit(() -> prepareAfter(start, actorId, companyId));

            start.countDown();

            var firstSource = first.get(20, TimeUnit.SECONDS);
            var secondSource = second.get(20, TimeUnit.SECONDS);

            assertThat(firstSource.id()).isEqualTo(secondSource.id());
            assertThat(sources.findAllByCompanyId(
                    companyId,
                    org.springframework.data.domain.Sort.by("id")).stream()
                    .filter(source -> source.getType() == AccountingSourceType.FILE_IMPORT)
                    .filter(source -> source.isActive()))
                    .hasSize(1);
        } finally {
            cleanup(companyId, actorId);
        }
    }

    private AccountingFileImportSourceResponse prepareAfter(
            CountDownLatch start,
            UUID actorId,
            UUID companyId) {
        try {
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Synthetic preparation did not start.");
            }
            return support.prepareSource(actorId, companyId);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    private void cleanup(UUID companyId, UUID actorId) {
        jdbc.update(
                "DELETE FROM company_access_grant_permissions WHERE company_access_grant_id IN " +
                        "(SELECT id FROM company_access_grants WHERE company_id = ?)",
                companyId);
        jdbc.update("DELETE FROM company_access_grants WHERE company_id = ?", companyId);
        jdbc.update("DELETE FROM accounting_sources WHERE company_id = ?", companyId);
        jdbc.update("DELETE FROM companies WHERE id = ?", companyId);
        jdbc.update("DELETE FROM user_accounts WHERE id = ?", actorId);
    }
}
