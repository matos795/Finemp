package com.finemp.accounting.fileimport;

import static org.assertj.core.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AccountingFileImportFoundationTest {
    @Test
    void shouldHashOnlyBoundedNonEmptyFiles() {
        String first = AccountingFileHash.sha256("synthetic".getBytes(StandardCharsets.UTF_8));
        String second = AccountingFileHash.sha256("synthetic".getBytes(StandardCharsets.UTF_8));

        assertThat(first).hasSize(64).isEqualTo(second);
        assertThatThrownBy(() -> AccountingFileHash.sha256(new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AccountingFileHash.sha256(
                new byte[AccountingFileImportLimits.MAX_FILE_BYTES + 1]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldFingerprintDatasetFileLayoutAndCoverageDeterministically() {
        String fileHash = "a".repeat(64);
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 1, 31);

        String first = AccountingFileImportFingerprint.calculate(
                AccountingFileDataset.GENERAL_LEDGER,
                fileHash,
                "synthetic-layout",
                start,
                end);
        String repeated = AccountingFileImportFingerprint.calculate(
                AccountingFileDataset.GENERAL_LEDGER,
                fileHash,
                "synthetic-layout",
                start,
                end);
        String otherPeriod = AccountingFileImportFingerprint.calculate(
                AccountingFileDataset.GENERAL_LEDGER,
                fileHash,
                "synthetic-layout",
                start,
                end.plusDays(1));

        assertThat(first).hasSize(64).isEqualTo(repeated).isNotEqualTo(otherPeriod);
    }

    @Test
    void shouldFailClosedWithoutAMatchingLayoutProfile() {
        var registry = new GeneralLedgerLayoutProfileRegistry(List.of());

        assertThatThrownBy(() -> registry.require("unknown", AccountingFileFormat.XLSX))
                .isInstanceOf(AccountingFileLayoutUnavailableException.class);
    }

    @Test
    void shouldRequireTheExplicitProfileAndSupportedFormat() {
        GeneralLedgerLayoutProfile profile = new GeneralLedgerLayoutProfile() {
            @Override public String id() { return "synthetic-v1"; }
            @Override public Set<AccountingFileFormat> formats() {
                return Set.of(AccountingFileFormat.XLSX);
            }
        };
        var registry = new GeneralLedgerLayoutProfileRegistry(List.of(profile));

        assertThat(registry.require("synthetic-v1", AccountingFileFormat.XLSX))
                .isSameAs(profile);
        assertThatThrownBy(() -> registry.require("synthetic-v1", AccountingFileFormat.CSV))
                .isInstanceOf(AccountingFileLayoutUnavailableException.class);
    }

    @Test
    void shouldRejectDuplicateOrInvalidLayoutRegistrationsAtStartup() {
        GeneralLedgerLayoutProfile first = profile("synthetic-v1");
        GeneralLedgerLayoutProfile duplicate = profile("synthetic-v1");
        GeneralLedgerLayoutProfile invalid = profile("Synthetic Layout");

        assertThatThrownBy(() -> new GeneralLedgerLayoutProfileRegistry(List.of(first, duplicate)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Duplicate accounting file layout profile.");
        assertThatThrownBy(() -> new GeneralLedgerLayoutProfileRegistry(List.of(invalid)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid accounting file layout profile.");
    }

    private GeneralLedgerLayoutProfile profile(String id) {
        return new GeneralLedgerLayoutProfile() {
            @Override public String id() { return id; }
            @Override public Set<AccountingFileFormat> formats() {
                return Set.of(AccountingFileFormat.CSV);
            }
        };
    }
}
