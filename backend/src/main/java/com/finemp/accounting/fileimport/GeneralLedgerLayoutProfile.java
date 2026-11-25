package com.finemp.accounting.fileimport;

import java.util.Set;

/**
 * Identifies one deterministic GENERAL_LEDGER export layout.
 * Parsing methods are introduced only with the first evidence-backed profile.
 */
public interface GeneralLedgerLayoutProfile {
    String id();

    Set<AccountingFileFormat> formats();
}
