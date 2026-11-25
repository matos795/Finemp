package com.finemp.integration.questor;

import com.finemp.accounting.reading.AccountingUnexpectedSourceDataException;

/** Questor-specific fail-closed signal; intentionally contains no accounting payload. */
public final class QuestorUnexpectedSourceDataException extends AccountingUnexpectedSourceDataException {
    public QuestorUnexpectedSourceDataException() {
        super();
    }
}
