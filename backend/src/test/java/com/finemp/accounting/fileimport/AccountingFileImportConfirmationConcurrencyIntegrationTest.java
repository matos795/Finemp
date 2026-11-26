package com.finemp.accounting.fileimport;

import static org.assertj.core.api.Assertions.assertThat;

import com.finemp.TestcontainersConfiguration;
import com.finemp.access.CompanyAccessGrant;
import com.finemp.access.CompanyAccessGrantRepository;
import com.finemp.access.CompanyAccessRole;
import com.finemp.access.CompanyPermission;
import com.finemp.access.CostCenterScope;
import com.finemp.access.UserAccount;
import com.finemp.access.UserAccountRepository;
import com.finemp.accounting.movement.AccountingMovementRepository;
import com.finemp.accounting.source.AccountingSource;
import com.finemp.accounting.source.AccountingSourceRepository;
import com.finemp.accounting.source.AccountingSourceType;
import com.finemp.company.Company;
import com.finemp.company.CompanyRepository;
import java.nio.charset.StandardCharsets;
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
class AccountingFileImportConfirmationConcurrencyIntegrationTest {

    @Autowired private AccountingFileImportConfirmationService confirmations;
    @Autowired private AccountingFileImportReceiptRepository receipts;
    @Autowired private AccountingSourceRepository sources;
    @Autowired private CompanyRepository companies;
    @Autowired private UserAccountRepository users;
    @Autowired private CompanyAccessGrantRepository grants;
    @Autowired private AccountingMovementRepository movements;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void concurrentReplayOfTheSameCommandPublishesOnlyOnce() throws Exception {
        var tx = new TransactionTemplate(transactionManager);
        UUID[] ids = tx.execute(status -> {
            var actor = users.saveAndFlush(new UserAccount(
                    "Synthetic Concurrent Confirmation",
                    UUID.randomUUID() + "@example.invalid",
                    "hash"));
            var company = companies.saveAndFlush(new Company(
                    "Synthetic Concurrent Confirmation",
                    "92929292000192"));
            var source = sources.saveAndFlush(new AccountingSource(
                    company,
                    AccountingSourceType.FILE_IMPORT,
                    null,
                    true));
            grants.saveAndFlush(new CompanyAccessGrant(
                    actor,
                    company,
                    CompanyAccessRole.CUSTOM,
                    CostCenterScope.ALL,
                    Set.of(CompanyPermission.ACCOUNTING_IMPORT_MANAGE)));
            return new UUID[] {actor.getId(), company.getId(), source.getId()};
        });

        UUID actorId = ids[0];
        UUID companyId = ids[1];
        UUID sourceId = ids[2];
        UUID command = UUID.randomUUID();
        byte[] file = fixture();
        var start = new CountDownLatch(1);

        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> confirmAfter(start, actorId, sourceId, command, file));
            var second = pool.submit(() -> confirmAfter(start, actorId, sourceId, command, file));

            start.countDown();

            var firstReceipt = first.get(20, TimeUnit.SECONDS);
            var secondReceipt = second.get(20, TimeUnit.SECONDS);

            assertThat(firstReceipt).isEqualTo(secondReceipt);
            assertThat(receipts.find(sourceId, command)).contains(firstReceipt);
            assertThat(movements.findAllForPublication(sourceId, Set.of("0/10"))).hasSize(1);
            assertThat(jdbc.queryForObject(
                    """
                    SELECT COUNT(*) FROM accounting_file_import_receipts
                    WHERE accounting_source_id = ? AND idempotency_key = ?
                    """,
                    Long.class,
                    sourceId,
                    command))
                    .isEqualTo(1L);
        } finally {
            cleanup(sourceId, companyId, actorId);
        }
    }

    private AccountingFileImportReceipt confirmAfter(
            CountDownLatch start,
            UUID actorId,
            UUID sourceId,
            UUID command,
            byte[] file) {
        try {
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Synthetic confirmation did not start.");
            }
            return confirmations.confirm(
                    actorId,
                    sourceId,
                    command,
                    AccountingFileHash.sha256(file),
                    AnalyticalLedgerHtmlLayoutProfile.PROFILE_ID,
                    AccountingFileFormat.HTML_XLS,
                    file);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    private void cleanup(UUID sourceId, UUID companyId, UUID actorId) {
        jdbc.update(
                "DELETE FROM accounting_dataset_coverage WHERE accounting_source_id = ?",
                sourceId);
        jdbc.update(
                "DELETE FROM accounting_file_import_receipts WHERE accounting_source_id = ?",
                sourceId);
        jdbc.update(
                """
                DELETE FROM accounting_movements
                WHERE accounting_source_id = ?
                """,
                sourceId);
        jdbc.update(
                "DELETE FROM accounting_accounts WHERE accounting_source_id = ?",
                sourceId);
        jdbc.update(
                "DELETE FROM company_access_grant_permissions WHERE company_access_grant_id IN " +
                        "(SELECT id FROM company_access_grants WHERE company_id = ?)",
                companyId);
        jdbc.update("DELETE FROM company_access_grants WHERE company_id = ?", companyId);
        jdbc.update("DELETE FROM accounting_sources WHERE id = ?", sourceId);
        jdbc.update("DELETE FROM companies WHERE id = ?", companyId);
        jdbc.update("DELETE FROM user_accounts WHERE id = ?", actorId);
    }

    private byte[] fixture() {
        return """
                <html><title>Razão Analítico</title><body><table>
                <tr><td>Consolidação: Empresa</td><td></td><td></td><td></td><td></td><td></td><td></td><td>Período: 01/01/2026 a 31/01/2026</td></tr>
                <tr><td>Data</td><td>Histórico</td><td></td><td>Lote/Lcto.</td><td></td><td>Débito</td><td>Crédito</td><td>Saldo</td></tr>
                <tr><td>Conta: 111010001</td><td>Red.: 1-9  CAIXA SINTETICO</td><td></td><td></td><td></td><td></td><td>Saldo Anterior:</td><td>0,00</td></tr>
                <tr><td>10/01/2026</td><td>MOV A</td><td></td><td>0/10</td><td></td><td>10,00</td><td></td><td>10,00</td></tr>
                </table></body></html>
                """.getBytes(StandardCharsets.ISO_8859_1);
    }
}
