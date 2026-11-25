package com.finemp.accounting.fileimport;

import static org.assertj.core.api.Assertions.*;

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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class AccountingFileImportReceiptRepositoryIntegrationTest {
    @Autowired private AccountingFileImportReceiptRepository receipts;
    @Autowired private AccountingSourceRepository sources;
    @Autowired private CompanyRepository companies;
    @Autowired private UserAccountRepository users;

    @Test
    void shouldPersistOnlyReceiptMetadataWithoutFilePayload() {
        var actor = users.saveAndFlush(new UserAccount(
                "Synthetic File Import",
                UUID.randomUUID() + "@example.invalid",
                "hash"));
        var company = companies.saveAndFlush(
                new Company("Synthetic Receipt", "78787878000178"));
        var source = sources.saveAndFlush(
                new AccountingSource(company, AccountingSourceType.FILE_IMPORT, null, true));
        UUID command = UUID.randomUUID();
        String fileHash = "a".repeat(64);
        String fingerprint = AccountingFileImportFingerprint.calculate(
                AccountingFileDataset.GENERAL_LEDGER,
                fileHash,
                "synthetic-v1",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31));

        receipts.insert(new AccountingFileImportReceipt(
                source.getId(),
                command,
                AccountingFileDataset.GENERAL_LEDGER,
                fileHash,
                fingerprint,
                "synthetic-v1",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                100,
                98,
                2,
                actor.getId(),
                Instant.parse("2026-02-01T12:00:00Z")));

        var stored = receipts.find(source.getId(), command).orElseThrow();

        assertThat(stored.fileSha256()).isEqualTo(fileHash);
        assertThat(stored.fingerprint()).isEqualTo(fingerprint);
        assertThat(stored.rowCount()).isEqualTo(100);
        assertThat(stored.publishedCount()).isEqualTo(98);
        assertThat(stored.skippedCount()).isEqualTo(2);
    }

    @Test
    void shouldScopeIdempotencyKeyByAccountingSource() {
        var actor = users.saveAndFlush(new UserAccount(
                "Synthetic Scope",
                UUID.randomUUID() + "@example.invalid",
                "hash"));
        var company = companies.saveAndFlush(
                new Company("Synthetic Scope Company", "79797979000179"));
        var first = sources.saveAndFlush(
                new AccountingSource(company, AccountingSourceType.FILE_IMPORT, null, true));
        var second = sources.saveAndFlush(
                new AccountingSource(company, AccountingSourceType.FILE_IMPORT, null, true));
        UUID command = UUID.randomUUID();

        receipts.insert(receipt(first.getId(), command, actor.getId(), "a"));
        receipts.insert(receipt(second.getId(), command, actor.getId(), "b"));

        assertThat(receipts.find(first.getId(), command)).isPresent();
        assertThat(receipts.find(second.getId(), command)).isPresent();
    }

    @Test
    void shouldRejectASecondReceiptForTheSameSourceAndIdempotencyKey() {
        var actor = users.saveAndFlush(new UserAccount(
                "Synthetic Duplicate",
                UUID.randomUUID() + "@example.invalid",
                "hash"));
        var company = companies.saveAndFlush(
                new Company("Synthetic Duplicate Company", "80808080000180"));
        var source = sources.saveAndFlush(
                new AccountingSource(company, AccountingSourceType.FILE_IMPORT, null, true));
        UUID command = UUID.randomUUID();

        receipts.insert(receipt(source.getId(), command, actor.getId(), "a"));

        assertThatThrownBy(() ->
                receipts.insert(receipt(source.getId(), command, actor.getId(), "b")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private AccountingFileImportReceipt receipt(
            UUID source,
            UUID command,
            UUID actor,
            String hashPrefix) {
        String fileHash = hashPrefix.repeat(64);
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 31);
        return new AccountingFileImportReceipt(
                source,
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
                actor,
                Instant.parse("2026-02-01T12:00:00Z"));
    }
}
