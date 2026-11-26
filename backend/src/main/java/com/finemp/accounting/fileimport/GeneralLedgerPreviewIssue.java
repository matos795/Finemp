package com.finemp.accounting.fileimport;

public record GeneralLedgerPreviewIssue(
        String code,
        Integer sourceRow,
        String message) {
}
