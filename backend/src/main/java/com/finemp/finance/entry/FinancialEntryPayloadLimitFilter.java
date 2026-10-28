package com.finemp.finance.entry;

import com.finemp.finance.http.FinancialCommandPayloadLimitFilter;

/** Backward-compatible entry route marker, limits and rejection codes. */
public class FinancialEntryPayloadLimitFilter extends FinancialCommandPayloadLimitFilter {
    public FinancialEntryPayloadLimitFilter(int maxBytes,int concurrency) {
        super(maxBytes,concurrency,EntryCommandAuthorizationManager.AUTHORIZED_WRITER_ATTRIBUTE,"FINANCIAL_ENTRY");
    }
}
