package com.finemp.finance.transfer;

import com.finemp.finance.http.FinancialCommandPayloadLimitFilter;

public class BankTransferPayloadLimitFilter extends FinancialCommandPayloadLimitFilter {
    public BankTransferPayloadLimitFilter(int maxBytes,int concurrency) {
        super(maxBytes,concurrency,BankTransferCommandAuthorizationManager.BODY_ATTRIBUTE,"BANK_TRANSFER");
    }
}
