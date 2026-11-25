package com.finemp.accounting.fileimport;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Objects;

public final class AccountingFileImportFingerprint {
    private AccountingFileImportFingerprint() {
    }

    public static String calculate(
            AccountingFileDataset dataset,
            String fileSha256,
            String layoutProfile,
            LocalDate coveredFrom,
            LocalDate coveredTo) {
        Objects.requireNonNull(dataset);
        if (fileSha256 == null || !fileSha256.matches("[0-9a-f]{64}")
                || layoutProfile == null || layoutProfile.isBlank()
                || layoutProfile.length() > AccountingFileImportLimits.MAX_LAYOUT_PROFILE_LENGTH
                || coveredFrom == null || coveredTo == null || coveredFrom.isAfter(coveredTo)) {
            throw new IllegalArgumentException("Accounting import fingerprint input is invalid.");
        }

        String canonical = String.join(
                "\n",
                "finemp-accounting-file-import-v1",
                dataset.name(),
                fileSha256,
                layoutProfile,
                coveredFrom.toString(),
                coveredTo.toString());

        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
