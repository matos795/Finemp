package com.finemp.accounting.fileimport;

import static org.assertj.core.api.Assertions.assertThat;

import com.finemp.TestcontainersConfiguration;
import com.finemp.access.UserAccount;
import com.finemp.access.UserAccountRepository;
import com.finemp.accounting.source.AccountingSource;
import com.finemp.accounting.source.AccountingSourceRepository;
import com.finemp.accounting.source.AccountingSourceType;
import com.finemp.company.Company;
import com.finemp.company.CompanyRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AccountingFileImportReceiptConcurrencyIntegrationTest {
    @Autowired private AccountingFileImportReceiptRepository receipts;
    @Autowired private AccountingSourceRepository sources;
    @Autowired private CompanyRepository companies;
    @Autowired private UserAccountRepository users;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void shouldCommitAtMostOneReceiptForConcurrentSameSourceAndIdempotencyKey() throws Exception {
        var tx = new TransactionTemplate(transactionManager);
        UUID[] ids = tx.execute(status -> {
            var actor = users.saveAndFlush(new UserAccount(
                    "Synthetic Concurrent Import",
                    UUID.randomUUID() + "@example.invalid",
                    "hash"));
            var company = companies.saveAndFlush(
                    new Company("Synthetic Concurrent Import", "83838383000183"));
            var source = sources.saveAndFlush(
                    new AccountingSource(company, AccountingSourceType.FILE_IMPORT, null, true));
            return new UUID[] {actor.getId(), company.getId(), source.getId()};
        });

        UUID actorId = ids[0];
        UUID companyId = ids[1];
        UUID sourceId = ids[2];
        UUID command = UUID.randomUUID();
        var start = new CountDownLatch(1);

        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> insertInOwnTransaction(
                    tx, start, receipt(sourceId, command, actorId)));
            var second = pool.submit(() -> insertInOwnTransaction(
                    tx, start, receipt(sourceId, command, actorId)));

            start.countDown();

            boolean firstSucceeded = first.get(15, TimeUnit.SECONDS);
            boolean secondSucceeded = second.get(15, TimeUnit.SECONDS);

            assertThat(firstSucceeded).isNotEqualTo(secondSucceeded);
            assertThat(jdbc.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM accounting_file_import_receipts
                    WHERE accounting_source_id = ? AND idempotency_key = ?
                    """,
                    Long.class,
                    sourceId,
                    command))
                    .isEqualTo(1L);
        } finally {
            jdbc.update(
                    "DELETE FROM accounting_file_import_receipts WHERE accounting_source_id = ?",
                    sourceId);
            jdbc.update("DELETE FROM accounting_sources WHERE id = ?", sourceId);
            jdbc.update("DELETE FROM companies WHERE id = ?", companyId);
            jdbc.update("DELETE FROM user_accounts WHERE id = ?", actorId);
        }
    }

    private boolean insertInOwnTransaction(
            TransactionTemplate tx,
            CountDownLatch start,
            AccountingFileImportReceipt receipt) {
        try {
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Synthetic concurrent import did not start.");
            }
            tx.executeWithoutResult(status -> receipts.insert(receipt));
            return true;
        } catch (DataIntegrityViolationException duplicate) {
            return false;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    private AccountingFileImportReceipt receipt(
            UUID sourceId,
            UUID command,
            UUID actorId) {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 31);
        String fileHash = "c".repeat(64);

        return new AccountingFileImportReceipt(
                sourceId,
                command,
                AccountingFileDataset.GENERAL_LEDGER,
                fileHash,
                AccountingFileImportFingerprint.calculate(
                        AccountingFileDataset.GENERAL_LEDGER,
                        fileHash,
                        "synthetic-v1",
                        from,
                        to),
                "synthetic-v1",
                from,
                to,
                10,
                10,
                0,
                actorId,
                Instant.parse("2026-02-01T12:00:00Z"));
    }
}
