package com.finemp.accounting.fileimport;

import java.util.Set;

/**
 * Identifies one deterministic GENERAL_LEDGER export layout.
 * Parsing is server-side and evidence-backed; confirmation always reparses the file.
 */
public interface GeneralLedgerLayoutProfile {
    String id();

    Set<AccountingFileFormat> formats();

    default GeneralLedgerParsedFile parse(byte[] content) {
        throw new AccountingFileLayoutUnavailableException();
    }

    default GeneralLedgerPreview preview(byte[] content) {
        return parse(content).toPreview();
    }
}
