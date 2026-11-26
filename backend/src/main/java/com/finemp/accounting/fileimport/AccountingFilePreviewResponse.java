package com.finemp.accounting.fileimport;

import java.time.LocalDate;
import java.util.List;

public record AccountingFilePreviewResponse(
        String fileSha256,
        AccountingFileFormat format,
        String layoutProfile,
        LocalDate periodStart,
        LocalDate periodEnd,
        int totalRows,
        int movementCount,
        int invalidRowCount,
        int observedAccountCount,
        List<GeneralLedgerPreviewIssue> warnings,
        List<GeneralLedgerPreviewIssue> errors,
        List<GeneralLedgerPreviewRow> sampleRows) {

    static AccountingFilePreviewResponse from(String fileSha256, GeneralLedgerPreview preview) {
        return new AccountingFilePreviewResponse(
                fileSha256,
                preview.format(),
                preview.layoutProfile(),
                preview.periodStart(),
                preview.periodEnd(),
                preview.totalRows(),
                preview.movementCount(),
                preview.invalidRowCount(),
                preview.observedAccountCount(),
                preview.warnings(),
                preview.errors(),
                preview.sampleRows());
    }
}
