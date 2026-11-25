package com.finemp.accounting.fileimport;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class AccountingFileHash {
    private AccountingFileHash() {
    }

    public static String sha256(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > AccountingFileImportLimits.MAX_FILE_BYTES) {
            throw new IllegalArgumentException("Accounting import file is invalid.");
        }

        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
