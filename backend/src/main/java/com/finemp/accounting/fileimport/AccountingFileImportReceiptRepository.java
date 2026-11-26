package com.finemp.accounting.fileimport;

import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AccountingFileImportReceiptRepository {
    private final JdbcTemplate jdbc;

    public AccountingFileImportReceiptRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<AccountingFileImportReceipt> find(
            UUID accountingSourceId,
            UUID idempotencyKey) {
        return jdbc.query(
                """
                SELECT accounting_source_id, idempotency_key, dataset,
                       file_sha256, fingerprint, layout_profile,
                       covered_from, covered_to, row_count,
                       published_count, skipped_count, created_by, committed_at
                FROM accounting_file_import_receipts
                WHERE accounting_source_id = ? AND idempotency_key = ?
                """,
                (row, index) -> new AccountingFileImportReceipt(
                        row.getObject("accounting_source_id", UUID.class),
                        row.getObject("idempotency_key", UUID.class),
                        AccountingFileDataset.valueOf(row.getString("dataset")),
                        row.getString("file_sha256"),
                        row.getString("fingerprint"),
                        row.getString("layout_profile"),
                        row.getObject("covered_from", java.time.LocalDate.class),
                        row.getObject("covered_to", java.time.LocalDate.class),
                        row.getLong("row_count"),
                        row.getLong("published_count"),
                        row.getLong("skipped_count"),
                        row.getObject("created_by", UUID.class),
                        row.getTimestamp("committed_at").toInstant()),
                accountingSourceId,
                idempotencyKey)
                .stream()
                .findFirst();
    }

    public Optional<AccountingFileImportReceipt> findOverlapping(
            UUID accountingSourceId,
            AccountingFileDataset dataset,
            java.time.LocalDate coveredFrom,
            java.time.LocalDate coveredTo) {
        return jdbc.query(
                """
                SELECT accounting_source_id, idempotency_key, dataset,
                       file_sha256, fingerprint, layout_profile,
                       covered_from, covered_to, row_count,
                       published_count, skipped_count, created_by, committed_at
                FROM accounting_file_import_receipts
                WHERE accounting_source_id = ?
                  AND dataset = ?
                  AND covered_from <= ?
                  AND covered_to >= ?
                ORDER BY committed_at ASC
                LIMIT 1
                """,
                (row, index) -> new AccountingFileImportReceipt(
                        row.getObject("accounting_source_id", UUID.class),
                        row.getObject("idempotency_key", UUID.class),
                        AccountingFileDataset.valueOf(row.getString("dataset")),
                        row.getString("file_sha256"),
                        row.getString("fingerprint"),
                        row.getString("layout_profile"),
                        row.getObject("covered_from", java.time.LocalDate.class),
                        row.getObject("covered_to", java.time.LocalDate.class),
                        row.getLong("row_count"),
                        row.getLong("published_count"),
                        row.getLong("skipped_count"),
                        row.getObject("created_by", UUID.class),
                        row.getTimestamp("committed_at").toInstant()),
                accountingSourceId,
                dataset.name(),
                coveredTo,
                coveredFrom)
                .stream()
                .findFirst();
    }

    public void insert(AccountingFileImportReceipt receipt) {
        jdbc.update(
                """
                INSERT INTO accounting_file_import_receipts(
                    accounting_source_id, idempotency_key, dataset,
                    file_sha256, fingerprint, layout_profile,
                    covered_from, covered_to, row_count,
                    published_count, skipped_count, created_by, committed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                receipt.accountingSourceId(),
                receipt.idempotencyKey(),
                receipt.dataset().name(),
                receipt.fileSha256(),
                receipt.fingerprint(),
                receipt.layoutProfile(),
                receipt.coveredFrom(),
                receipt.coveredTo(),
                receipt.rowCount(),
                receipt.publishedCount(),
                receipt.skippedCount(),
                receipt.createdBy(),
                Timestamp.from(receipt.committedAt()));
    }
}
